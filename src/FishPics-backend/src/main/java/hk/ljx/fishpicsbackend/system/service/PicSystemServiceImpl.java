package hk.ljx.fishpicsbackend.system.service;
import hk.ljx.fishpicsbackend.system.entity.PicSystem;
import hk.ljx.fishpicsbackend.common.utils.XssSanitizer;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.cache.MultiLevelCacheManager;
import hk.ljx.fishpicsbackend.common.utils.DistributedLock;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.mapper.PicSystemMapper;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.system.dto.AddSysMarquee;
import hk.ljx.fishpicsbackend.system.dto.AddSysPicType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.SysConstants.MARQUESS_KEY;
import static hk.ljx.fishpicsbackend.common.constants.SysConstants.TYPE_LIST_KEY;

/**
 * 系统配置表（pic_system）的 Service 实现
 * 用 key-value 方式存储系统级配置（图片标签列表、跑马灯、AI开关等）
 * 所有配置变更后都会清除多级缓存，保证下次读取拿到最新值
 */
@Service
@Slf4j
public class PicSystemServiceImpl extends ServiceImpl<PicSystemMapper, PicSystem>
    implements PicSystemService{

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private MultiLevelCacheManager cacheManager;

    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    /**
     * sysvalue JSON 解析失败兜底返回 empty list
     */
    private List<String> safeParseList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> parsed = JSONUtil.toList(json, String.class);
            return parsed != null ? parsed : List.of();
        } catch (Exception e) {
            log.warn("[PicSystem] sysvalue JSON 解析失败,fallback empty list: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getTypeList() {
        // 多级缓存：L1(Caffeine) → L2(Redis)
        Object cached = cacheManager.getSysConfigCache().get(TYPE_LIST_KEY);
        if (cached instanceof List) {
            return (List<String>) cached;
        }

        // 缓存miss，查数据库
        LambdaQueryWrapper<PicSystem> queryWrapper = new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, TYPE_LIST_KEY);
        List<PicSystem> list = baseMapper.selectList(queryWrapper);
        // 无配置时返回空列表（与 getMarquess 行为一致，管理员可通过后台新增）
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        PicSystem picSystem = list.get(0);
        if (picSystem.getSysvalue() == null) {
            return List.of();
        }
        List<String> result = safeParseList(picSystem.getSysvalue());

        // 写入多级缓存
        cacheManager.getSysConfigCache().put(TYPE_LIST_KEY, result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTypeList(AddSysPicType addSysPicType) {
        ExcUtils.throwIfTrue(addSysPicType.getValue() == null || addSysPicType.getValue().isEmpty(), ExceptionCode.PARAMETER_ERROR, "标签不能为空");
        // 管理员输入的标签值过 XSS 清理后再入库
        List<String> sanitized = addSysPicType.getValue().stream()
                .map(XssSanitizer::clean)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toList());
        ExcUtils.throwIfTrue(sanitized.isEmpty(), ExceptionCode.PARAMETER_ERROR, "标签清理后为空,疑似非法输入");
        addSysPicType.setValue(sanitized);
        // 锁 TTL 30s（临界区内含多次 DB 操作）
        DistributedLock lock = new DistributedLock(stringRedisTemplate, "LOCK:SYS:TYPE_LIST", 30);
        if (!lock.tryLock()) {
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS, "其他节点正在修改,请稍后再试");
        }
        try {
        List<PicSystem> list = baseMapper.selectList(
                new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, TYPE_LIST_KEY));
        PicSystem picSystem;
        if (list == null || list.isEmpty()) {
            picSystem = new PicSystem();
            picSystem.setSyskey(TYPE_LIST_KEY);
            picSystem.setSysvalue(JSONUtil.toJsonStr(addSysPicType.getValue()));
            baseMapper.insert(picSystem);
        } else {
            picSystem = list.get(0);
            List<String> typeList = new ArrayList<>(safeParseList(picSystem.getSysvalue()));
            // 去重添加，防止标签列表无限膨胀
            for (String item : addSysPicType.getValue()) {
                if (!typeList.contains(item)) {
                    typeList.add(item);
                }
            }
            picSystem.setSysvalue(JSONUtil.toJsonStr(typeList));
            baseMapper.updateById(picSystem);
            // 清理历史脏数据残留(多行)
            if (list.size() > 1) {
                for (int i = 1; i < list.size(); i++) {
                    baseMapper.deleteById(list.get(i).getId());
                }
            }
        }
        // 清除缓存
        cacheManager.getSysConfigCache().evict(TYPE_LIST_KEY);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deleteType(String type) {
        ExcUtils.throwIfTrue(type == null || type.trim().isEmpty(), ExceptionCode.PARAMETER_ERROR, "标签名不能为空");
        // 用 Redis 分布式锁替代 synchronized
        // 锁 TTL 30s（临界区内含多次 DB 操作）
        DistributedLock lock = new DistributedLock(stringRedisTemplate, "LOCK:SYS:TYPE_LIST", 30);
        if (!lock.tryLock()) {
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS, "其他节点正在修改,请稍后再试");
        }
        try {
        List<PicSystem> list = baseMapper.selectList(
                new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, TYPE_LIST_KEY));
        ExcUtils.throwIfTrue(list == null || list.isEmpty(), ExceptionCode.NOT_FOUND, "标签配置不存在");
        PicSystem picSystem = list.get(0);
        ExcUtils.throwIfTrue(picSystem.getSysvalue() == null, ExceptionCode.NOT_FOUND, "标签配置不存在");
        List<String> typeList = new ArrayList<>(safeParseList(picSystem.getSysvalue()));
        boolean removed = typeList.remove(type);
        ExcUtils.throwIfTrue(!removed, ExceptionCode.NOT_FOUND, "标签不存在");
        picSystem.setSysvalue(JSONUtil.toJsonStr(typeList));
        baseMapper.updateById(picSystem);
        // 清除缓存
        cacheManager.getSysConfigCache().evict(TYPE_LIST_KEY);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getMarquess() {
        // 多级缓存：L1(Caffeine) → L2(Redis)
        Object cached = cacheManager.getSysConfigCache().get(MARQUESS_KEY);
        if (cached instanceof List) {
            return (List<String>) cached;
        }

        // 缓存miss，查数据库
        LambdaQueryWrapper<PicSystem> queryWrapper = new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, MARQUESS_KEY);
        List<PicSystem> list = baseMapper.selectList(queryWrapper);
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        PicSystem picSystem = list.get(0);
        if (picSystem.getSysvalue() == null) {
            return List.of();
        }
        List<String> result = safeParseList(picSystem.getSysvalue());

        // 写入多级缓存
        cacheManager.getSysConfigCache().put(MARQUESS_KEY, result);
        return result;
    }

    @Override
    public void addMarquee(AddSysMarquee addSysMarquee) {
        ExcUtils.throwIfTrue(addSysMarquee.getPictureId() == null || addSysMarquee.getPictureId().isEmpty(), ExceptionCode.PARAMETER_ERROR, "图片id不能为空");
        // 用 Redis 分布式锁替代 synchronized
        // 锁 TTL 30s（临界区内含多次 DB 操作）
        DistributedLock lock = new DistributedLock(stringRedisTemplate, "LOCK:SYS:MARQUESS", 30);
        if (!lock.tryLock()) {
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS, "其他节点正在修改,请稍后再试");
        }
        try {

        List<Long> idList;
        try {
            idList = addSysMarquee.getPictureId().stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "图片ID必须为数字");
        }
        List<Picture> pictures = pictureMapper.selectList(new LambdaQueryWrapper<Picture>().in(Picture::getId, idList));
        // 之前 pictures.size() != idList.size() 在 pictureId 重复时(如 [1,1,2])会误报,
        // 改为用 Set 比对唯一 ID 数
        java.util.Set<Long> requestedIds = new java.util.HashSet<>(idList);
        java.util.Set<Long> foundIds = pictures.stream().map(Picture::getId).collect(Collectors.toSet());
        ExcUtils.throwIfTrue(!requestedIds.equals(foundIds), ExceptionCode.NOT_FOUND, "部分图片不存在，请检查所有图片ID");

        List<String> marquess = pictures.stream().map(Picture::getUrl).collect(Collectors.toList());

        // 修复:同上,selectList + get(0) + insert/updateById 替代 saveOrUpdate(无主键 insert 会污染)
        List<PicSystem> list = baseMapper.selectList(
                new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, MARQUESS_KEY));

        PicSystem picSystem;
        if (list == null || list.isEmpty()) {
            picSystem = new PicSystem();
            picSystem.setSyskey(MARQUESS_KEY);
            picSystem.setSysvalue(JSONUtil.toJsonStr(marquess));
            baseMapper.insert(picSystem);
        } else {
            picSystem = list.get(0);
            List<String> oldMarquess = new ArrayList<>(safeParseList(picSystem.getSysvalue()));
            // 去重添加，防止跑马灯列表无限膨胀
            for (String url : marquess) {
                if (!oldMarquess.contains(url)) {
                    oldMarquess.add(url);
                }
            }
            picSystem.setSysvalue(JSONUtil.toJsonStr(oldMarquess));
            baseMapper.updateById(picSystem);
            if (list.size() > 1) {
                for (int i = 1; i < list.size(); i++) {
                    baseMapper.deleteById(list.get(i).getId());
                }
            }
        }
        // 清除缓存
        cacheManager.getSysConfigCache().evict(MARQUESS_KEY);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void deleteMarquee(String url) {
        ExcUtils.throwIfTrue(url == null || url.trim().isEmpty(), ExceptionCode.PARAMETER_ERROR, "图片url不能为空");
        // 用 Redis 分布式锁替代 synchronized
        // 锁 TTL 30s（临界区内含多次 DB 操作）
        DistributedLock lock = new DistributedLock(stringRedisTemplate, "LOCK:SYS:MARQUESS", 30);
        if (!lock.tryLock()) {
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS, "其他节点正在修改,请稍后再试");
        }
        try {
        // 修复:同上
        List<PicSystem> list = baseMapper.selectList(
                new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, MARQUESS_KEY));
        ExcUtils.throwIfTrue(list == null || list.isEmpty(), ExceptionCode.NOT_FOUND, "跑马灯配置不存在");
        PicSystem picSystem = list.get(0);
        ExcUtils.throwIfTrue(picSystem.getSysvalue() == null, ExceptionCode.NOT_FOUND, "跑马灯配置不存在");
        List<String> marquess = new ArrayList<>(safeParseList(picSystem.getSysvalue()));
        boolean removed = marquess.remove(url);
        ExcUtils.throwIfTrue(!removed, ExceptionCode.NOT_FOUND, "该图片不在跑马灯列表中");
        picSystem.setSysvalue(JSONUtil.toJsonStr(marquess));
        baseMapper.updateById(picSystem);
        // 清除缓存
        cacheManager.getSysConfigCache().evict(MARQUESS_KEY);
        } finally {
            lock.unlock();
        }
    }
}




