package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.dto.space.CreateSpace;
import hk.ljx.fishpicsbackend.dto.space.SpacePictureList;
import hk.ljx.fishpicsbackend.dto.space.SpaceQueryWrapper;
import hk.ljx.fishpicsbackend.dto.space.UpdateSpace;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.entity.Space;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.service.LoginUser;
import hk.ljx.fishpicsbackend.service.SpaceService;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.vo.picture.PictureListVO;
import hk.ljx.fishpicsbackend.vo.picture.PicturePageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.*;

import static hk.ljx.fishpicsbackend.common.constants.SpaceConstants.*;
import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

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
    @Transactional(rollbackFor = Exception.class)
    public Boolean createSpace(CreateSpace createSpace,User user) {
        String name = createSpace.getName();
        String introduction = createSpace.getIntroduction();
        Integer type = createSpace.getType();
        ExcUtils.throwIfTrue(name == null || type == null, "空间名称不能为空");
        // 获取创建用户
        Integer level = user.getLevel();
        ExcUtils.throwIfTrue(user == null || user.getId() == null, "用户不存在");
        // 判断空间类型
        List<Space> spaceList = spaceMapper.selectList(new QueryWrapper<Space>().eq("user_id", user.getId()).eq("type", type));
        if (type == 0) {
            ExcUtils.throwIfTrue(!spaceList.isEmpty(), "私人空间已存在");
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

    @Override
    public Boolean updateSpace(UpdateSpace updateSpace, HttpServletRequest request) {
        Long id = updateSpace.getId();
        String name = updateSpace.getName();
        String introduction = updateSpace.getIntroduction();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(name), ExceptionCode.PARAMETER_ERROR, "空间名称不能为空");

        User user = loginUser.getLoginUser(request);
        Long userId = user.getId();
        // 1. 查询空间是否存在
        Space space = spaceMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");
        // 2. 判断用户是否为空间主人或管理员
        ExcUtils.throwIfFalse(space.getUserId().equals(userId) || user.getRole().equals(ADMIN), ExceptionCode.PARAMETER_ERROR, "无权限修改空间信息");
        // 3. 更新空间信息
        space.setName(name);
        space.setIntroduction(introduction);
        int update = spaceMapper.update(space, new QueryWrapper<Space>().eq("id", id));
        ExcUtils.throwIfTrue(update <= 0, ExceptionCode.PARAMETER_ERROR, "更新空间信息失败");
        return true;
    }

    @Override
    public PicturePageVO pictureList(SpacePictureList spacePictureList, HttpServletRequest request) {
        Long spaceId = spacePictureList.getSpaceId();
        int current = spacePictureList.getCurrent();
        int pageSize = spacePictureList.getPageSize();
        String sortField = spacePictureList.getSortField();
        String sortOrder = spacePictureList.getSortOrder();

        ExcUtils.throwIfTrue(spaceId == null, ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        User user = loginUser.getLoginUser(request);
        Long userId = user.getId();
        // 1. 查询是否为自己的空间
        Space space = spaceMapper.selectById(spaceId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space) || !Objects.equals(space.getUserId(), userId), ExceptionCode.PARAMETER_ERROR, "空间不存在或无权限");
        // 2. 查询图片列表
        Page<Picture> picturePage = new Page<>(current, pageSize);
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        pictureQueryWrapper.eq("space_id", spaceId);
        pictureQueryWrapper.isNull("parent_id");
        pictureQueryWrapper.orderBy(ObjectUtil.isNotNull(sortField), sortOrder.equals("ascend"), sortField);
        Page<Picture> pictureList = pictureMapper.selectPage(picturePage, pictureQueryWrapper);
        ArrayList<PictureListVO> pictureListVOS = new ArrayList<>();
        pictureList.getRecords().forEach(picture -> {
            PictureListVO pictureListVO = new PictureListVO();
            pictureListVO.setId(picture.getId());
            pictureListVO.setUrl(picture.getUrl());
            pictureListVOS.add(pictureListVO);
        });
        return new PicturePageVO(pictureListVOS, pictureList.getTotal());
    }

    @Override
    public QueryWrapper<Space> getSpaceQueryWrapper(SpaceQueryWrapper spaceQueryWrapper) {
        Long id = spaceQueryWrapper.getId();
        String introduction = spaceQueryWrapper.getIntroduction();
        Integer type = spaceQueryWrapper.getType();
        String teamUsersId = spaceQueryWrapper.getTeamUsersId();
        Long userId = spaceQueryWrapper.getUserId();
        Long storageSize = spaceQueryWrapper.getStorageSize();
        Integer level = spaceQueryWrapper.getLevel();
        String name = spaceQueryWrapper.getName();
        String sortField = spaceQueryWrapper.getSortField();
        String sortOrder = spaceQueryWrapper.getSortOrder();

        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(!ObjectUtil.isEmpty(id), "id", id);
        queryWrapper.eq(!ObjectUtil.isEmpty(introduction), "introduction", introduction);
        queryWrapper.eq(!ObjectUtil.isEmpty(type), "type", type);
        queryWrapper.eq(!ObjectUtil.isEmpty(teamUsersId), "team_users_id", teamUsersId);
        queryWrapper.eq(!ObjectUtil.isEmpty(userId), "user_id", userId);
        queryWrapper.eq(!ObjectUtil.isEmpty(storageSize), "storage_size", storageSize);
        queryWrapper.eq(!ObjectUtil.isEmpty(level), "level", level);
        queryWrapper.eq(!ObjectUtil.isEmpty(name), "name", name);
        queryWrapper.orderBy(ObjectUtil.isNotNull(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }
}




