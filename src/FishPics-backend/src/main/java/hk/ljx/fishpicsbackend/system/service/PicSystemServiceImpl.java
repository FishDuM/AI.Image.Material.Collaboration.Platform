package hk.ljx.fishpicsbackend.system.service;
import hk.ljx.fishpicsbackend.system.entity.PicSystem;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.cache.MultiLevelCacheManager;
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
public class PicSystemServiceImpl extends ServiceImpl<PicSystemMapper, PicSystem>
    implements PicSystemService{

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private MultiLevelCacheManager cacheManager;

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
        ExcUtils.throwIfTrue(list == null || list.isEmpty(), "标签不存在");
        PicSystem picSystem = list.get(0);
        ExcUtils.throwIfTrue(picSystem.getSysvalue() == null, "标签不存在");
        List<String> result = JSONUtil.toList(picSystem.getSysvalue(), String.class);

        // 写入多级缓存
        cacheManager.getSysConfigCache().put(TYPE_LIST_KEY, result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized void addTypeList(AddSysPicType addSysPicType) {
        ExcUtils.throwIfTrue(addSysPicType.getValue() == null || addSysPicType.getValue().isEmpty(), ExceptionCode.PARAMETER_ERROR, "标签不能为空");
        LambdaQueryWrapper<PicSystem> queryWrapper = new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, TYPE_LIST_KEY);
        List<PicSystem> list = baseMapper.selectList(queryWrapper);
        PicSystem picSystem;
        if (list == null || list.isEmpty()) {
            picSystem = new PicSystem();
            picSystem.setSyskey(TYPE_LIST_KEY);
            picSystem.setSysvalue(JSONUtil.toJsonStr(addSysPicType.getValue()));
        } else {
            picSystem = list.get(0);
            List<String> typeList = JSONUtil.toList(picSystem.getSysvalue(), String.class);
            // 去重添加，防止标签列表无限膨胀
            for (String item : addSysPicType.getValue()) {
                if (!typeList.contains(item)) {
                    typeList.add(item);
                }
            }
            picSystem.setSysvalue(JSONUtil.toJsonStr(typeList));
            for (int i = 1; i < list.size(); i++) {
                baseMapper.deleteById(list.get(i).getId());
            }
        }
        this.saveOrUpdate(picSystem);
        // 清除缓存
        cacheManager.getSysConfigCache().evict(TYPE_LIST_KEY);
    }

    @Override
    public synchronized void deleteType(String type) {
        ExcUtils.throwIfTrue(type == null || type.trim().isEmpty(), ExceptionCode.PARAMETER_ERROR, "标签名不能为空");
        LambdaQueryWrapper<PicSystem> queryWrapper = new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, TYPE_LIST_KEY);
        List<PicSystem> list = baseMapper.selectList(queryWrapper);
        ExcUtils.throwIfTrue(list == null || list.isEmpty(), ExceptionCode.NOT_FOUND, "标签配置不存在");
        PicSystem picSystem = list.get(0);
        ExcUtils.throwIfTrue(picSystem.getSysvalue() == null, ExceptionCode.NOT_FOUND, "标签配置不存在");
        List<String> typeList = new ArrayList<>(JSONUtil.toList(picSystem.getSysvalue(), String.class));
        boolean removed = typeList.remove(type);
        ExcUtils.throwIfTrue(!removed, ExceptionCode.NOT_FOUND, "标签不存在");
        picSystem.setSysvalue(JSONUtil.toJsonStr(typeList));
        this.saveOrUpdate(picSystem);
        // 清除缓存
        cacheManager.getSysConfigCache().evict(TYPE_LIST_KEY);
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
        List<String> result = JSONUtil.toList(picSystem.getSysvalue(), String.class);

        // 写入多级缓存
        cacheManager.getSysConfigCache().put(MARQUESS_KEY, result);
        return result;
    }

    @Override
    public synchronized void addMarquee(AddSysMarquee addSysMarquee) {
        ExcUtils.throwIfTrue(addSysMarquee.getPictureId() == null || addSysMarquee.getPictureId().isEmpty(), ExceptionCode.PARAMETER_ERROR, "图片id不能为空");

        List<Long> idList;
        try {
            idList = addSysMarquee.getPictureId().stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        } catch (NumberFormatException e) {
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "图片ID必须为数字");
        }
        List<Picture> pictures = pictureMapper.selectList(new LambdaQueryWrapper<Picture>().in(Picture::getId, idList));
        ExcUtils.throwIfTrue(pictures.size() != idList.size(), ExceptionCode.NOT_FOUND, "部分图片不存在，请检查所有图片ID");

        List<String> marquess = pictures.stream().map(Picture::getUrl).collect(Collectors.toList());

        LambdaQueryWrapper<PicSystem> queryWrapper = new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, MARQUESS_KEY);
        List<PicSystem> list = baseMapper.selectList(queryWrapper);

        PicSystem picSystem;
        if (list == null || list.isEmpty()) {
            picSystem = new PicSystem();
            picSystem.setSyskey(MARQUESS_KEY);
            picSystem.setSysvalue(JSONUtil.toJsonStr(marquess));
        } else {
            picSystem = list.get(0);
            List<String> oldMarquess = JSONUtil.toList(picSystem.getSysvalue(), String.class);
            // 去重添加，防止跑马灯列表无限膨胀
            for (String url : marquess) {
                if (!oldMarquess.contains(url)) {
                    oldMarquess.add(url);
                }
            }
            picSystem.setSysvalue(JSONUtil.toJsonStr(oldMarquess));
            for (int i = 1; i < list.size(); i++) {
                baseMapper.deleteById(list.get(i).getId());
            }
        }
        this.saveOrUpdate(picSystem);
        // 清除缓存
        cacheManager.getSysConfigCache().evict(MARQUESS_KEY);
    }

    @Override
    public synchronized void deleteMarquee(String url) {
        ExcUtils.throwIfTrue(url == null || url.trim().isEmpty(), ExceptionCode.PARAMETER_ERROR, "图片url不能为空");
        LambdaQueryWrapper<PicSystem> queryWrapper = new LambdaQueryWrapper<PicSystem>().eq(PicSystem::getSyskey, MARQUESS_KEY);
        List<PicSystem> list = baseMapper.selectList(queryWrapper);
        ExcUtils.throwIfTrue(list == null || list.isEmpty(), ExceptionCode.NOT_FOUND, "跑马灯配置不存在");
        PicSystem picSystem = list.get(0);
        ExcUtils.throwIfTrue(picSystem.getSysvalue() == null, ExceptionCode.NOT_FOUND, "跑马灯配置不存在");
        List<String> marquess = new ArrayList<>(JSONUtil.toList(picSystem.getSysvalue(), String.class));
        boolean removed = marquess.remove(url);
        ExcUtils.throwIfTrue(!removed, ExceptionCode.NOT_FOUND, "该图片不在跑马灯列表中");
        picSystem.setSysvalue(JSONUtil.toJsonStr(marquess));
        this.saveOrUpdate(picSystem);
        // 清除缓存
        cacheManager.getSysConfigCache().evict(MARQUESS_KEY);
    }
}




