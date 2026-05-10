package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
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
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.service.LoginUser;
import hk.ljx.fishpicsbackend.service.PictureService;
import hk.ljx.fishpicsbackend.service.SpaceService;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.vo.picture.PictureListVO;
import hk.ljx.fishpicsbackend.vo.picture.PicturePageVO;
import hk.ljx.fishpicsbackend.vo.space.SpaceMemberVO;
import hk.ljx.fishpicsbackend.vo.space.SpaceVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.util.*;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.SpaceConstants.*;
import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

/** 空间服务实现类 */
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private PictureService pictureService;

    @Resource
    private LoginUser loginUser;

    @Resource
    private UserMapper userMapper;

    /**
     * 创建空间，根据用户等级和空间类型分配存储配额
     * @param createSpace 创建空间请求参数
     * @param user 当前登录用户
     * @return 创建成功返回true
     */
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
        // 判断空间类型并校验数量限制
        // 私人空间每人只能有一个，团队空间根据等级有不同上限
        List<Space> spaceList = spaceMapper.selectList(new QueryWrapper<Space>().eq("user_id", user.getId()).eq("type", type));
        if (type == 0) {
            // 私人空间：每人限一个
            ExcUtils.throwIfTrue(!spaceList.isEmpty(), "私人空间已存在");
        } else if (type == 1) {
            // 团队空间：根据用户等级限制数量（普通1个/VIP 5个/SVIP 10个）
            if (level == 0) {
                ExcUtils.throwIfTrue(CollUtil.size(spaceList) >= TEAM_MAX_COUNT_LEVEL0, "团队空间已达到上限");
            } else if (level == 1) {
                ExcUtils.throwIfTrue(CollUtil.size(spaceList) >= TEAM_MAX_COUNT_LEVEL1, "团队空间已达到上限");
            } else if (level == 2) {
                ExcUtils.throwIfTrue(CollUtil.size(spaceList) >= TEAM_MAX_COUNT_LEVEL2, "团队空间已达到上限");
            }
        }
        // 根据用户等级和空间类型分配存储配额
        // 私人空间：普通512MB / VIP 5GB / SVIP 10GB
        // 团队空间：普通512MB / VIP 30GB / SVIP 50GB
        Space space = new Space();
        if (type == 0) {
            // 私人空间存储配额
            if (level == 0) {
                space.setLevel(0);
                space.setStorageSize(DEFAULT_STORAGE_SIZE);
            } else if (level == 1) {
                space.setLevel(1);
                space.setStorageSize(VIP_STORAGE_SIZE);
            } else if (level == 2) {
                space.setLevel(2);
                space.setStorageSize(SVIP_STORAGE_SIZE);
            }
        } else if (type == 1) {
            // 团队空间存储配额（比私人空间更大）
            if (level == 0) {
                space.setLevel(0);
                space.setStorageSize(DEFAULT_STORAGE_SIZE);
            } else if (level == 1) {
                space.setLevel(1);
                space.setStorageSize(TEAM_VIP_STORAGE_SIZE);
            } else if (level == 2) {
                space.setLevel(2);
                space.setStorageSize(TEAM_SVIP_STORAGE_SIZE);
            }
        }
        space.setName(name);
        space.setIntroduction(introduction);
        space.setType(type);
        space.setUserId(user.getId());
        if (type == 1) {
            space.setTeamUsersId(JSONUtil.toJsonStr(Collections.singletonList(user.getId())));
        }
        int insert = spaceMapper.insert(space);
        ExcUtils.throwIfTrue(insert <= 0, "创建空间失败");
        return true;
    }

    /**
     * 获取当前用户的空间列表
     * @param type 空间类型：0-私人空间，1-团队空间
     * @param request HTTP请求
     * @return 空间VO列表（含图片数量、创建人、成员信息）
     */
    @Override
    public List<SpaceVO> listSpace(Integer type, HttpServletRequest request) {
        User user = loginUser.getLoginUser(request);
        Long userId = user.getId();

        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId).eq("type", type);
        List<Space> spaceList = spaceMapper.selectList(queryWrapper);
        if (CollUtil.isEmpty(spaceList)) {
            return new ArrayList<>();
        }

        Set<Long> allUserIds = new HashSet<>();
        for (Space space : spaceList) {
            if (space.getUserId() != null) {
                allUserIds.add(space.getUserId());
            }
            parseTeamUserIds(space.getTeamUsersId()).forEach(allUserIds::add);
        }
        Map<Long, User> userMap = new HashMap<>();
        if (!allUserIds.isEmpty()) {
            userMap = userMapper.selectBatchIds(allUserIds)
                    .stream().collect(Collectors.toMap(User::getId, u -> u));
        }

        List<Long> spaceIds = spaceList.stream().map(Space::getId).collect(Collectors.toList());
        Map<Long, Long> pictureCountMap = new HashMap<>();
        List<Map<String, Object>> countResult = pictureService.listMaps(
                new QueryWrapper<Picture>()
                        .select("space_id", "COUNT(*) as cnt")
                        .in("space_id", spaceIds)
                        .groupBy("space_id")
        );
        for (Map<String, Object> row : countResult) {
            Long sid = ((Number) row.get("space_id")).longValue();
            Long cnt = ((Number) row.get("cnt")).longValue();
            pictureCountMap.put(sid, cnt);
        }

        List<SpaceVO> voList = new ArrayList<>();
        for (Space space : spaceList) {
            SpaceVO vo = new SpaceVO(space);
            vo.setPictureCount(pictureCountMap.getOrDefault(space.getId(), 0L));
            User creator = userMap.get(space.getUserId());
            if (creator != null) {
                vo.setUserName(creator.getNickname());
                vo.setUserAvatar(creator.getAvatar());
            }
            if (type == 1) {
                List<Long> memberIds = parseTeamUserIds(space.getTeamUsersId());
                List<SpaceMemberVO> members = memberIds.stream()
                        .limit(10)
                        .map(userMap::get)
                        .filter(Objects::nonNull)
                        .map(m -> new SpaceMemberVO(m.getId(), m.getNickname(), m.getAvatar()))
                        .collect(Collectors.toList());
                vo.setTeamMembers(members);
            }
            voList.add(vo);
        }
        return voList;
    }

    /**
     * 获取单个空间详情
     * @param id 空间ID
     * @param request HTTP请求
     * @return 空间VO（含图片数量、创建人、成员信息）
     */
    @Override
    public SpaceVO getSpace(Long id, HttpServletRequest request) {
        ExcUtils.throwIfTrue(id == null, ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        User user = loginUser.getLoginUser(request);
        Long userId = user.getId();

        Space space = spaceMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");

        boolean isCreator = Objects.equals(space.getUserId(), userId);
        boolean isTeamMember = false;
        if (space.getType() == 1) {
            List<Long> memberIds = parseTeamUserIds(space.getTeamUsersId());
            isTeamMember = memberIds.contains(userId);
        }
        ExcUtils.throwIfTrue(!isCreator && !isTeamMember, ExceptionCode.PARAMETER_ERROR, "无权限访问该空间");

        SpaceVO vo = new SpaceVO(space);

        Set<Long> userIds = new HashSet<>();
        if (space.getUserId() != null) userIds.add(space.getUserId());
        parseTeamUserIds(space.getTeamUsersId()).forEach(userIds::add);
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMap = userMapper.selectBatchIds(userIds)
                    .stream().collect(Collectors.toMap(User::getId, u -> u));
        }

        User creator = userMap.get(space.getUserId());
        if (creator != null) {
            vo.setUserName(creator.getNickname());
            vo.setUserAvatar(creator.getAvatar());
        }

        List<Map<String, Object>> countResult = pictureService.listMaps(
                new QueryWrapper<Picture>()
                        .select("COUNT(*) as cnt")
                        .eq("space_id", id)
        );
        long picCount = 0;
        if (!countResult.isEmpty() && countResult.get(0).get("cnt") != null) {
            picCount = ((Number) countResult.get(0).get("cnt")).longValue();
        }
        vo.setPictureCount(picCount);

        if (space.getType() == 1) {
            List<Long> memberIds = parseTeamUserIds(space.getTeamUsersId());
            List<SpaceMemberVO> members = memberIds.stream()
                    .limit(10)
                    .map(userMap::get)
                    .filter(Objects::nonNull)
                    .map(m -> new SpaceMemberVO(m.getId(), m.getNickname(), m.getAvatar()))
                    .collect(Collectors.toList());
            vo.setTeamMembers(members);
        }

        return vo;
    }

    private List<Long> parseTeamUserIds(String teamUsersId) {
        if (teamUsersId == null || teamUsersId.trim().isEmpty() || "[]".equals(teamUsersId.trim())) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(teamUsersId, Long.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 更新空间信息
     * @param updateSpace 更新请求参数
     * @param request HTTP请求
     * @return 更新成功返回true
     */
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
        // 2. 权限校验：仅空间创建者或管理员可修改
        ExcUtils.throwIfFalse(space.getUserId().equals(userId) || user.getRole().equals(ADMIN), ExceptionCode.PARAMETER_ERROR, "无权限修改空间信息");
        // 3. 更新空间信息
        space.setName(name);
        space.setIntroduction(introduction);
        int update = spaceMapper.update(space, new QueryWrapper<Space>().eq("id", id));
        ExcUtils.throwIfTrue(update <= 0, ExceptionCode.PARAMETER_ERROR, "更新空间信息失败");
        return true;
    }

    /**
     * 获取空间图片列表（分页）
     * @param spacePictureList 查询参数
     * @param request HTTP请求
     * @return 图片分页结果
     */
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
        Space space = spaceMapper.selectById(spaceId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在或无权限");
        boolean isCreator = Objects.equals(space.getUserId(), userId);
        boolean isTeamMember = space.getType() == 1 && parseTeamUserIds(space.getTeamUsersId()).contains(userId);
        ExcUtils.throwIfTrue(!isCreator && !isTeamMember, ExceptionCode.PARAMETER_ERROR, "空间不存在或无权限");
        // 2. 分页查询图片列表
        Page<Picture> picturePage = new Page<>(current, pageSize);
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        pictureQueryWrapper.eq("space_id", spaceId);
        pictureQueryWrapper.orderBy(ObjectUtil.isNotNull(sortField), sortOrder.equals("ascend"), sortField);
        Page<Picture> pictureList = pictureService.page(picturePage, pictureQueryWrapper);
        // 3. 转换为VO（仅返回id和url，不暴露完整图片元数据）
        ArrayList<PictureListVO> pictureListVOS = new ArrayList<>();
        pictureList.getRecords().forEach(picture -> {
            PictureListVO pictureListVO = new PictureListVO();
            pictureListVO.setId(picture.getId());
            pictureListVO.setUrl(picture.getUrl());
            pictureListVOS.add(pictureListVO);
        });
        return new PicturePageVO(pictureListVOS, pictureList.getTotal());
    }

    /**
     * 构建空间查询条件包装器
     * @param spaceQueryWrapper 查询条件包装器
     * @return QueryWrapper对象
     */
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




