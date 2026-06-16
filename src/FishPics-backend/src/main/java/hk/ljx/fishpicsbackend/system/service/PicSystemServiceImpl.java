package hk.ljx.fishpicsbackend.system.service;
import hk.ljx.fishpicsbackend.system.entity.PicSystem;
import hk.ljx.fishpicsbackend.common.utils.XssSanitizer;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.cache.MultiLevelCacheManager;
import hk.ljx.fishpicsbackend.common.utils.DistributedLockService;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.mapper.PicSystemMapper;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.system.dto.AddSysMarqueeRequest;
import hk.ljx.fishpicsbackend.system.dto.AddSysPicTypeRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private DistributedLockService distributedLockService;

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
    public void addTypeList(AddSysPicTypeRequest addSysPicType) {
        ExcUtils.throwIfTrue(addSysPicType.getValue() == null || addSysPicType.getValue().isEmpty(), ExceptionCode.PARAMETER_ERROR, "标签不能为空");
        // 管理员输入的标签值过 XSS 清理后再入库
        List<String> sanitized = addSysPicType.getValue().stream()
                .map(XssSanitizer::clean)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toList());
        ExcUtils.throwIfTrue(sanitized.isEmpty(), ExceptionCode.PARAMETER_ERROR, "标签清理后为空,疑似非法输入");
        addSysPicType.setValue(sanitized);
        upsertConfigList("LOCK:SYS:TYPE_LIST", TYPE_LIST_KEY, sanitized, true);
    }

    @Override
    public void deleteType(String type) {
        ExcUtils.throwIfTrue(type == null || type.trim().isEmpty(), ExceptionCode.PARAMETER_ERROR, "标签名不能为空");
        removeFromConfigList("LOCK:SYS:TYPE_LIST", TYPE_LIST_KEY, type);
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
    public void addMarquee(AddSysMarqueeRequest addSysMarquee) {
        ExcUtils.throwIfTrue(addSysMarquee.getPictureIds() == null || addSysMarquee.getPictureIds().isEmpty(), ExceptionCode.PARAMETER_ERROR, "图片id不能为空");

        List<Long> idList;
        try {
            idList = addSysMarquee.getPictureIds().stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "图片ID必须为数字");
        }
        List<Picture> pictures = pictureMapper.selectList(new LambdaQueryWrapper<Picture>().in(Picture::getId, idList));
        Set<Long> requestedIds = new HashSet<>(idList);
        Set<Long> foundIds = pictures.stream().map(Picture::getId).collect(Collectors.toSet());
        ExcUtils.throwIfTrue(!requestedIds.equals(foundIds), ExceptionCode.NOT_FOUND, "部分图片不存在，请检查所有图片ID");

        List<String> marqueeUrls = pictures.stream().map(Picture::getUrl).collect(Collectors.toList());
        upsertConfigList("LOCK:SYS:MARQUESS", MARQUESS_KEY, marqueeUrls, true);
    }

    @Override
    public void deleteMarquee(String url) {
        ExcUtils.throwIfTrue(url == null || url.trim().isEmpty(), ExceptionCode.PARAMETER_ERROR, "图片url不能为空");
        removeFromConfigList("LOCK:SYS:MARQUESS", MARQUESS_KEY, url);
    }

    /**
     * 通用的配置列表更新（加锁 → 查现有 → 去重合并/新建 → 更新 → 清缓存）
     * 用于 addTypeList / addMarquee
     *
     * @param lockKey   Redis 分布式锁 key
     * @param configKey pic_system 表的 syskey
     * @param newItems  要合并的新列表
     * @param dedup     是否去重
     */
    private void upsertConfigList(String lockKey, String configKey, List<String> newItems, boolean dedup) {
        if (!distributedLockService.tryLock(lockKey, 30)) {
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS, "其他节点正在修改,请稍后再试");
        }
        try {
            List<PicSystem> list = baseMapper.selectList(
                    new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, configKey));
            PicSystem picSystem;
            if (list == null || list.isEmpty()) {
                picSystem = new PicSystem();
                picSystem.setSyskey(configKey);
                picSystem.setSysvalue(JSONUtil.toJsonStr(newItems));
                baseMapper.insert(picSystem);
            } else {
                picSystem = list.get(0);
                List<String> existing = new ArrayList<>(safeParseList(picSystem.getSysvalue()));
                for (String item : newItems) {
                    if (!dedup || !existing.contains(item)) {
                        existing.add(item);
                    }
                }
                picSystem.setSysvalue(JSONUtil.toJsonStr(existing));
                baseMapper.updateById(picSystem);
                cleanupDuplicateKeys(list);
            }
            cacheManager.getSysConfigCache().evict(configKey);
        } finally {
            distributedLockService.unlock(lockKey);
        }
    }

    /**
     * 通用的配置列表删除（加锁 → 查现有 → 移除 → 更新 → 清缓存）
     * 用于 deleteType / deleteMarquee
     *
     * @param lockKey    Redis 分布式锁 key
     * @param configKey  pic_system 表的 syskey
     * @param itemToRemove 要移除的项
     */
    private void removeFromConfigList(String lockKey, String configKey, String itemToRemove) {
        if (!distributedLockService.tryLock(lockKey, 30)) {
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS, "其他节点正在修改,请稍后再试");
        }
        try {
            List<PicSystem> list = baseMapper.selectList(
                    new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, configKey));
            ExcUtils.throwIfTrue(list == null || list.isEmpty(), ExceptionCode.NOT_FOUND, "配置不存在");
            PicSystem picSystem = list.get(0);
            ExcUtils.throwIfTrue(picSystem.getSysvalue() == null, ExceptionCode.NOT_FOUND, "配置不存在");
            List<String> items = new ArrayList<>(safeParseList(picSystem.getSysvalue()));
            boolean removed = items.remove(itemToRemove);
            ExcUtils.throwIfTrue(!removed, ExceptionCode.NOT_FOUND, "该项不存在");
            picSystem.setSysvalue(JSONUtil.toJsonStr(items));
            baseMapper.updateById(picSystem);
            cacheManager.getSysConfigCache().evict(configKey);
        } finally {
            distributedLockService.unlock(lockKey);
        }
    }

    /**
     * 清理 syskey 重复的多余记录（保留第一条，删除后续）
     */
    private void cleanupDuplicateKeys(List<PicSystem> list) {
        if (list.size() > 1) {
            for (int i = 1; i < list.size(); i++) {
                baseMapper.deleteById(list.get(i).getId());
            }
        }
    }
}




