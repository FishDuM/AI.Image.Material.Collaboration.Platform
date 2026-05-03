package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.dto.space.CreateSpace;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.entity.Space;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.service.LoginUser;
import hk.ljx.fishpicsbackend.service.SpaceService;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.SpaceConstants.*;

/**
* @author abc
* @description 针对表【space(空间表)】的数据库操作Service实现
* @createDate 2026-05-03 15:29:23
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private LoginUser loginUser;

    @Override
    public Boolean createSpace(CreateSpace createSpace, HttpServletRequest request) {
        String name = createSpace.getName();
        String introduction = createSpace.getIntroduction();
        Integer type = createSpace.getType();
        ExcUtils.throwIfTrue(name == null || type == null, "空间名称不能为空");
        // 获取创建用户
        User user = loginUser.getLoginUser(request);
        Integer level = user.getLevel();
        ExcUtils.throwIfTrue(user == null && user.getId() == null, "用户不存在");
        // 判断空间类型
        List<Space> spaceList = spaceMapper.selectList(new QueryWrapper<Space>().eq("user_id", user.getId()).eq("type", type));
        if (type == 0) {
            ExcUtils.throwIfTrue(CollUtil.isNotEmpty(spaceList), "私人空间已存在");
        } else if (type == 1) {
            if (level == 0) {
                ExcUtils.throwIfTrue(CollUtil.isNotEmpty(spaceList), "团队空间已存在");
            } else if (level == 1) {
                ExcUtils.throwIfTrue(CollUtil.size(spaceList) >= 3, "团队空间已达到上限");
            } else if (level == 2) {
                ExcUtils.throwIfTrue(CollUtil.size(spaceList) >= 6, "团队空间已达到上限");
            }
        }
        Space space = new Space();
        if (level == 0) {
            // 普通用户
            space.setLevel(0);
            space.setStorageSize(DEFAULT_STORAGE_SIZE);
        } else if (level == 1) {
            // VIP
            space.setLevel(1);
            space.setStorageSize(VIP_STORAGE_SIZE);
        } else if (level == 2) {
            // SVIP
            space.setLevel(2);
            space.setStorageSize(SVIP_STORAGE_SIZE);
        }
        space.setName(name);
        space.setIntroduction(introduction);
        space.setType(type);
        space.setUserId(user.getId());
        int insert = spaceMapper.insert(space);
        ExcUtils.throwIfTrue(insert <= 0, "创建空间失败");
        return true;
    }

    @Override
    public List<Space> listSpace(Integer type, HttpServletRequest request) {
        // 1. 获取登录用户
        User user = loginUser.getLoginUser(request);
        Long userId = user.getId();

        // 2. 查询当前用户的空间列表
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("type", type);
        return spaceMapper.selectList(queryWrapper);
    }
}




