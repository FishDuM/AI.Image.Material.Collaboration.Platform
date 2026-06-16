package hk.ljx.fishpicsbackend.space.service;
import hk.ljx.fishpicsbackend.space.entity.Space;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.DistributedLockService;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.entity.PictureShare;
import hk.ljx.fishpicsbackend.picture.service.FileResourceService;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import hk.ljx.fishpicsbackend.picture.vo.PicturePageVO;
import hk.ljx.fishpicsbackend.space.dto.CreateSpace;
import hk.ljx.fishpicsbackend.space.dto.SpaceAdminUpdateRequest;
import hk.ljx.fishpicsbackend.space.dto.SpacePictureList;
import hk.ljx.fishpicsbackend.space.dto.SpaceQueryWrapper;
import hk.ljx.fishpicsbackend.space.dto.UpdateSpace;
import hk.ljx.fishpicsbackend.space.vo.SpaceMemberVO;
import hk.ljx.fishpicsbackend.space.vo.SpaceVO;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.space.dto.TeamInviteRequest;
import hk.ljx.fishpicsbackend.space.dto.TeamRemoveRequest;
import hk.ljx.fishpicsbackend.space.dto.TeamChangeRoleRequest;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.mapper.PictureShareMapper;
import hk.ljx.fishpicsbackend.collab.CollabSessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.Resource;

import java.util.*;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.SpaceConstants.*;

/** 空间服务实现类 */
@Slf4j
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    /**
     * 角色名称映射（简化：仅保留 OWNER 和 MEMBER）
     */
    private static final Map<Integer, String> ROLE_NAME_MAP = Map.of(
            1, "所有者",
            2, "成员"
    );

    @Resource
    private PictureService pictureService;

    @Resource
    private FileResourceService fileResourceService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    @Resource
    private CollabSessionRegistry collabSessionRegistry;

    @Resource
    private PictureShareMapper pictureShareMapper;

    @Resource
    private DistributedLockService distributedLockService;

    /**
     * 创建空间，根据用户等级和空间类型分配存储配额
     *
     * @param createSpace 创建空间请求参数
     * @param user        当前登录用户
     * @return 创建成功返回true
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean createSpace(CreateSpace createSpace, User user) {
        String name = createSpace.getName();
        String introduction = createSpace.getIntroduction();
        Integer type = createSpace.getType();
        ExcUtils.throwIfTrue(name == null || type == null, "空间名称不能为空");
        ExcUtils.throwIfTrue(type != 0 && type != 1, "空间类型不合法，仅支持 0（私人空间）或 1（团队空间）");
        ExcUtils.throwIfTrue(user == null || user.getId() == null, "用户不存在");

        // 读取用户等级（admin 默认按 SVIP 算）
        Integer level = user.getLevel() != null ? user.getLevel() : 0;
        if (user.getRole() != null && user.getRole() == 1) {
            level = Math.max(level, 2);
        }

        // 加 per-user 分布式锁防止 check-then-act 竞态
        String privateLockKey = type == 0 ? "LOCK:SPACE:CREATE:PRIVATE:" + user.getId() : null;
        String teamLockKey = type == 1 ? "LOCK:SPACE:CREATE:TEAM:" + user.getId() : null;
        if (privateLockKey != null && !distributedLockService.tryLock(privateLockKey, 5)) {
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS, "其他请求正在创建您的私人空间,请稍后再试");
        }
        if (teamLockKey != null && !distributedLockService.tryLock(teamLockKey, 5)) {
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS, "其他请求正在创建团队空间,请稍后再试");
        }
        try {
        // 判断空间类型并校验数量限制
        List<Space> spaceList = baseMapper
                .selectList(new LambdaQueryWrapper<Space>().eq(Space::getUserId, user.getId()).eq(Space::getType, type));
        if (type == 0) {
            // 私人空间：每人限一个(锁内二次校验,防止锁内其他事务并入)
            ExcUtils.throwIfTrue(!spaceList.isEmpty(), "私人空间已存在");
        } else if (type == 1) {
            // 团队空间：数量上限按等级
            int maxTeamCount;
            if (level >= 2) {
                maxTeamCount = TEAM_MAX_COUNT_LEVEL2;
            } else if (level >= 1) {
                maxTeamCount = TEAM_MAX_COUNT_LEVEL1;
            } else {
                maxTeamCount = TEAM_MAX_COUNT_LEVEL0;
            }
            ExcUtils.throwIfTrue(CollUtil.size(spaceList) >= maxTeamCount,
                    "团队空间已达到上限（最多" + maxTeamCount + "个）");
        }

        // 存储配额：按用户等级 + 空间类型
        Space space = new Space();
        space.setLevel(level);
        if (type == 0) {
            // 私人空间
            space.setStorageSize(getPrivateStorageSize(level));
        } else if (type == 1) {
            // 团队空间
            space.setStorageSize(getTeamStorageSize(level));
        }
        space.setName(name);
        space.setIntroduction(introduction);
        space.setType(type);
        space.setUserId(user.getId());
        int insert = baseMapper.insert(space);
        ExcUtils.throwIfTrue(insert <= 0, "创建空间失败");
        if (type == 1) {
            // 团队空间创建者默认为所有者（role_id=1）
            SpaceTeamMember teamMember = new SpaceTeamMember();
            teamMember.setSpaceId(space.getId());
            teamMember.setUserId(user.getId());
            teamMember.setRoleId(1);
            spaceTeamMemberMapper.insert(teamMember);
        }
        return true;
        } finally {
            if (privateLockKey != null) {
                try { distributedLockService.unlock(privateLockKey); } catch (Exception e) { log.warn("释放私有空间创建锁失败: {}", e.getMessage()); }
            }
            if (teamLockKey != null) {
                try { distributedLockService.unlock(teamLockKey); } catch (Exception e) { log.warn("释放团队空间创建锁失败: {}", e.getMessage()); }
            }
        }
    }

    /**
     * 获取当前用户的空间列表
     * 
     * @param type 空间类型：0-私人空间，1-团队空间
     * @return 空间VO列表（含图片数量、创建人、成员信息）
     */
    @Override
    public List<SpaceVO> listSpace(Integer type) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(type == null, ExceptionCode.PARAMETER_ERROR, "空间类型不能为空");
        Long userId = user.getId();

        LambdaQueryWrapper<Space> queryWrapper = new LambdaQueryWrapper<>();
        if (type == 1) {
            // 团队空间：包含用户创建的 + 用户作为成员加入的
            List<Long> memberSpaceIds = spaceTeamMemberMapper.selectList(
                    new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getUserId, userId))
                    .stream().map(SpaceTeamMember::getSpaceId).collect(Collectors.toList());
            if (memberSpaceIds.isEmpty()) {
                queryWrapper.eq(Space::getUserId, userId)
                        .eq(Space::getType, type)
                        .eq(Space::getStatus, 1);
            } else {
                queryWrapper.and(w -> w.eq(Space::getUserId, userId).or().in(Space::getId, memberSpaceIds))
                        .eq(Space::getType, type)
                        .eq(Space::getStatus, 1);
            }
        } else {
            queryWrapper.eq(Space::getUserId, userId)
                    .eq(Space::getType, type)
                    .eq(Space::getStatus, 1);
        }
        List<Space> spaceList = baseMapper.selectList(queryWrapper);
        if (CollUtil.isEmpty(spaceList)) {
            return new ArrayList<>();
        }

        Set<Long> allUserIds = new HashSet<>();
        for (Space space : spaceList) {
            if (space.getUserId() != null) {
                allUserIds.add(space.getUserId());
            }
        }
        // 批量查询所有空间的成员（消除 N+1 查询）
        List<Long> spaceIds = spaceList.stream().map(Space::getId).collect(Collectors.toList());
        Map<Long, List<SpaceTeamMember>> membersBySpaceId = new HashMap<>();
        if (type == 1 && !spaceIds.isEmpty()) {
            List<SpaceTeamMember> allMembers = spaceTeamMemberMapper.selectList(
                    new LambdaQueryWrapper<SpaceTeamMember>().in(SpaceTeamMember::getSpaceId, spaceIds));
            membersBySpaceId = allMembers.stream()
                    .collect(Collectors.groupingBy(SpaceTeamMember::getSpaceId));
            allMembers.stream().map(SpaceTeamMember::getUserId).forEach(allUserIds::add);
        }
        Map<Long, User> userMap = new HashMap<>();
        if (!allUserIds.isEmpty()) {
            userMap = userMapper.selectByIds(allUserIds)
                    .stream().collect(Collectors.toMap(User::getId, u -> u));
        }

        Map<Long, Long> pictureCountMap = new HashMap<>();
        List<Map<String, Object>> countResult = pictureService.listMaps(
                new QueryWrapper<Picture>()
                        .select("space_id", "COUNT(*) as cnt")
                        .in("space_id", spaceIds)
                        .groupBy("space_id"));
        for (Map<String, Object> row : countResult) {
            Long sid = ((Number) row.get("space_id")).longValue();
            Long cnt = ((Number) row.get("cnt")).longValue();
            pictureCountMap.put(sid, cnt);
        }

        List<SpaceVO> voList = new ArrayList<>();
        for (Space space : spaceList) {
            List<SpaceTeamMember> teamMembers = type == 1
                    ? membersBySpaceId.getOrDefault(space.getId(), Collections.emptyList())
                    : null;
            voList.add(buildSpaceVO(space, userMap, pictureCountMap, teamMembers, 10));
        }
        return voList;
    }

    /**
     * 获取单个空间详情
     * 
     * @param id 空间ID
     * @return 空间VO（含图片数量、创建人、成员信息）
     */
    @Override
    public SpaceVO getSpace(Long id) {
        ExcUtils.throwIfTrue(id == null, ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        User user = UserHolder.getUser();
        Long userId = user.getId();

        Space space = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");
        validateSpaceActive(space);

        boolean isCreator = Objects.equals(space.getUserId(), userId);
        boolean isTeamMember = false;
        // 批量查询一次成员列表（权限校验 + VO 构建复用，消除 N+1）
        List<SpaceTeamMember> teamMembers = Collections.emptyList();
        if (Integer.valueOf(1).equals(space.getType())) {
            teamMembers = spaceTeamMemberMapper.selectList(new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getSpaceId, space.getId()));
            isTeamMember = teamMembers.stream().anyMatch(m -> m.getUserId().equals(userId));
        }
        ExcUtils.throwIfTrue(!isCreator && !isTeamMember, ExceptionCode.FORBIDDEN, "无权限访问该空间");

        Set<Long> userIds = new HashSet<>();
        if (space.getUserId() != null)
            userIds.add(space.getUserId());
        teamMembers.stream().map(SpaceTeamMember::getUserId).forEach(userIds::add);
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMap = userMapper.selectByIds(userIds)
                    .stream().collect(Collectors.toMap(User::getId, u -> u));
        }

        List<Map<String, Object>> countResult = pictureService.listMaps(
                new QueryWrapper<Picture>()
                        .select("COUNT(*) as cnt")
                        .eq("space_id", id));
        long picCount = 0;
        if (!countResult.isEmpty() && countResult.get(0).get("cnt") != null) {
            picCount = ((Number) countResult.get(0).get("cnt")).longValue();
        }
        Map<Long, Long> pictureCountMap = Map.of(space.getId(), picCount);

        List<SpaceTeamMember> teamMembersForVO = Integer.valueOf(1).equals(space.getType()) && !teamMembers.isEmpty()
                ? teamMembers : null;
        return buildSpaceVO(space, userMap, pictureCountMap, teamMembersForVO, 0);
    }

    /**
     * 更新空间信息
     * 
     * @param updateSpace 更新请求参数
     * @return 更新成功返回true
     */
    @Override
    public Boolean updateSpace(UpdateSpace updateSpace) {
        Long id = updateSpace.getId();
        String name = updateSpace.getName();
        String introduction = updateSpace.getIntroduction();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(name), ExceptionCode.PARAMETER_ERROR, "空间名称不能为空");

        User user = UserHolder.getUser();
        Long userId = user.getId();
        // 1. 查询空间是否存在
        Space space = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");
        validateSpaceActive(space);
        // 2. 权限校验：空间创建者 / 系统管理员 / team OWNER 都可修改(与 teamInvite/teamRemove 一致)
        boolean isCreator = java.util.Objects.equals(space.getUserId(), userId);
        boolean isSystemAdmin = user.getRole() != null && user.getRole() == 1;
        boolean isTeamOwner = !isCreator && Integer.valueOf(1).equals(space.getType()) && isTeamOwner(space.getId(), userId);
        // 无权限是 FORBIDDEN，不是 PARAMETER_ERROR
        ExcUtils.throwIfFalse(isCreator || isSystemAdmin || isTeamOwner,
                ExceptionCode.FORBIDDEN, "无权限修改空间信息");
        // 3. 仅更新允许修改的字段（避免全字段覆盖）
        Space updateObj = new Space();
        updateObj.setId(id);
        updateObj.setName(name);
        updateObj.setIntroduction(introduction);
        int update = baseMapper.updateById(updateObj);
        ExcUtils.throwIfTrue(update <= 0, ExceptionCode.PARAMETER_ERROR, "更新空间信息失败");
        return true;
    }

    /**
     * 获取空间图片列表（分页）
     * 
     * @param spacePictureList 查询参数
     * @return 图片分页结果
     */
    @Override
    public PicturePageVO pictureList(SpacePictureList spacePictureList) {
        Long spaceId = spacePictureList.getSpaceId();
        int current = spacePictureList.getCurrent();
        int pageSize = spacePictureList.getPageSize();
        String sortField = spacePictureList.getSortField();
        String sortOrder = spacePictureList.getSortOrder();

        ExcUtils.throwIfTrue(spaceId == null, ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        User user = UserHolder.getUser();
        Long userId = user.getId();
        Space space = baseMapper.selectById(spaceId);
        // 不区分"不存在"和"无权访问",防枚举攻击
        if (ObjectUtil.isEmpty(space)) {
            log.debug("space picture list: spaceId={} 不存在或无权访问(user={})", spaceId, userId);
            throw new BaseException(ExceptionCode.FORBIDDEN, "无权访问该空间");
        }
        validateSpaceActive(space);
        boolean isCreator = Objects.equals(space.getUserId(), userId);
        boolean isTeamMember = Integer.valueOf(1).equals(space.getType()) && spaceTeamMemberMapper.selectCount(new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getSpaceId, spaceId).eq(SpaceTeamMember::getUserId, userId)) > 0;
        if (!isCreator && !isTeamMember) {
            log.debug("space picture list: user={} 非 spaceId={} 成员", userId, spaceId);
            throw new BaseException(ExceptionCode.FORBIDDEN, "无权访问该空间");
        }
        // 2. 分页查询图片列表
        Page<Picture> picturePage = new Page<>(current, pageSize);
        QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
        pictureQueryWrapper.eq("space_id", spaceId);
        String keyword = spacePictureList.getKeyword();
        if (keyword != null && !keyword.trim().isEmpty()) {
            pictureQueryWrapper.and(w -> w
                    .like("picture_name", keyword)
                    .or()
                    .like("introduction", keyword));
        }
        // 排序字段白名单，防止 SQL 注入
        Set<String> allowedPictureSortFields = Set.of("id", "picture_name", "introduction", "url", "space_id",
                "user_id", "create_time", "update_time");
        boolean isPictureSortFieldValid = sortField != null && allowedPictureSortFields.contains(sortField);
        if (isPictureSortFieldValid) {
            pictureQueryWrapper.orderBy(true, "ascend".equals(sortOrder), sortField);
        } else {
            pictureQueryWrapper.orderByDesc("create_time");
        }
        Page<Picture> pictureList = pictureService.page(picturePage, pictureQueryWrapper);
        // 3. 转换为VO（仅返回id和url，不暴露完整图片元数据）
        ArrayList<PictureVO> pictureVOS = new ArrayList<>();
        pictureList.getRecords().forEach(picture -> {
            PictureVO pictureVO = PictureVO.ofUpload(picture.getId(), picture.getUrl());
            pictureVOS.add(pictureVO);
        });
        return new PicturePageVO(pictureVOS, pictureList.getTotal());
    }

    /**
     * 构建空间查询条件包装器（内部使用）
     */
    private QueryWrapper<Space> getSpaceQueryWrapper(SpaceQueryWrapper spaceQueryWrapper) {
        Long id = spaceQueryWrapper.getId();
        String introduction = spaceQueryWrapper.getIntroduction();
        Integer type = spaceQueryWrapper.getType();
        Long userId = spaceQueryWrapper.getUserId();
        Long storageSize = spaceQueryWrapper.getStorageSize();
        Integer level = spaceQueryWrapper.getLevel();
        String name = spaceQueryWrapper.getName();
        String sortField = spaceQueryWrapper.getSortField();
        String sortOrder = spaceQueryWrapper.getSortOrder();

        // 排序字段白名单，防止 SQL 注入
        Set<String> allowedSortFields = Set.of("id", "introduction", "type", "user_id", "storage_size", "level", "name", "size", "create_time", "update_time");
        boolean isSortFieldValid = sortField != null && allowedSortFields.contains(sortField);

        // 使用 QueryWrapper 支持动态排序字段（orderBy boolean, boolean, String）
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (!ObjectUtil.isEmpty(id)) queryWrapper.eq("id", id);
        if (!ObjectUtil.isEmpty(introduction)) queryWrapper.eq("introduction", introduction);
        if (!ObjectUtil.isEmpty(type)) queryWrapper.eq("type", type);
        if (!ObjectUtil.isEmpty(userId)) queryWrapper.eq("user_id", userId);
        if (!ObjectUtil.isEmpty(storageSize)) queryWrapper.eq("storage_size", storageSize);
        if (!ObjectUtil.isEmpty(level)) queryWrapper.eq("level", level);
        if (!ObjectUtil.isEmpty(name)) queryWrapper.eq("name", name);
        queryWrapper.orderBy(isSortFieldValid, "ascend".equals(sortOrder), sortField);
        return queryWrapper;
    }

    @Override
    public IPage<SpaceVO> adminList(SpaceQueryWrapper spaceQueryWrapper) {
        QueryWrapper<Space> queryWrapper = getSpaceQueryWrapper(spaceQueryWrapper);
        Page<Space> page = new Page<>(spaceQueryWrapper.getCurrent(), spaceQueryWrapper.getPageSize());
        Page<Space> spacePage = baseMapper.selectPage(page, queryWrapper);
        List<Space> spaceList = spacePage.getRecords();
        if (CollUtil.isEmpty(spaceList)) {
            Page<SpaceVO> emptyPage = new Page<>(spaceQueryWrapper.getCurrent(), spaceQueryWrapper.getPageSize(), spacePage.getTotal());
            emptyPage.setRecords(new ArrayList<>());
            return emptyPage;
        }
        Set<Long> allUserIds = new HashSet<>();
        for (Space space : spaceList) {
            if (space.getUserId() != null) allUserIds.add(space.getUserId());
        }
        // 批量查询所有空间的成员（消除 N+1 查询）
        List<Long> spaceIds = spaceList.stream().map(Space::getId).collect(Collectors.toList());
        List<SpaceTeamMember> allMembers = spaceTeamMemberMapper.selectList(
                new LambdaQueryWrapper<SpaceTeamMember>().in(SpaceTeamMember::getSpaceId, spaceIds));
        Map<Long, List<SpaceTeamMember>> membersBySpaceId = allMembers.stream()
                .collect(Collectors.groupingBy(SpaceTeamMember::getSpaceId));
        allMembers.stream().map(SpaceTeamMember::getUserId).forEach(allUserIds::add);
        Map<Long, User> userMap = new HashMap<>();
        if (!allUserIds.isEmpty()) {
            userMap = userMapper.selectByIds(allUserIds)
                    .stream().collect(Collectors.toMap(User::getId, u -> u));
        }
        Map<Long, Long> pictureCountMap = new HashMap<>();
        List<Map<String, Object>> countResult = pictureService.listMaps(
                new QueryWrapper<Picture>()
                        .select("space_id", "COUNT(*) as cnt")
                        .in("space_id", spaceIds)
                        .groupBy("space_id"));
        for (Map<String, Object> row : countResult) {
            Long sid = ((Number) row.get("space_id")).longValue();
            Long cnt = ((Number) row.get("cnt")).longValue();
            pictureCountMap.put(sid, cnt);
        }
        List<SpaceVO> voList = new ArrayList<>();
        for (Space space : spaceList) {
            List<SpaceTeamMember> teamMembers = Integer.valueOf(1).equals(space.getType())
                    ? membersBySpaceId.getOrDefault(space.getId(), Collections.emptyList()) : null;
            voList.add(buildSpaceVO(space, userMap, pictureCountMap, teamMembers, 10));
        }
        Page<SpaceVO> voPage = new Page<>(spaceQueryWrapper.getCurrent(), spaceQueryWrapper.getPageSize(), spacePage.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Boolean adminUpdate(SpaceAdminUpdateRequest request) {
        Long id = request.getId();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        Space space = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");
        // 仅更新允许的字段，防止越权修改 userId/type 等敏感字段
        Space updateObj = new Space();
        updateObj.setId(id);
        if (request.getName() != null) updateObj.setName(request.getName());
        if (request.getIntroduction() != null) updateObj.setIntroduction(request.getIntroduction());
        if (request.getLevel() != null) updateObj.setLevel(request.getLevel());
        if (request.getStorageSize() != null) updateObj.setStorageSize(request.getStorageSize());
        boolean result = this.updateById(updateObj);
        ExcUtils.throwIfTrue(!result, ExceptionCode.DATABASE_ERROR, "更新失败");
        return true;
    }

    /**
     * 删除空间（管理员接口）
     * 级联清理：file_resource 引用计数递减 → 删除团队成员 → 删除图片记录 → 删除空间本身
     * COS 物理文件由 file_resource 的引用计数机制管理，不是在这里直接删
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean adminDelete(Long id) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        Space space = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");

        // 1. 查询空间内所有图片，清理 COS 文件（仅在无其他图片依赖时删除）
        List<Picture> pictures = pictureService.list(
                new LambdaQueryWrapper<Picture>().eq(Picture::getSpaceId, id));
        for (Picture pic : pictures) {
            if (pic.getResourceId() != null) {
                // 通过 file_resource 引用计数机制管理 COS 文件生命周期
                // decrementRefCount 会在 ref_count 归零时自动删除 COS 文件
                try {
                    fileResourceService.decrementRefCount(pic.getResourceId());
                } catch (Exception e) {
                    log.warn("清理图片资源引用失败: pictureId={}, resourceId={}", pic.getId(), pic.getResourceId(), e);
                }
            }
        }

        // 删除空间前先强制断开该空间所有在线用户的 WS 连接
        final Long spaceIdForWs = id;
        final List<Picture> picturesForWs = pictures;
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            java.util.Set<Long> onlineUserIds = collabSessionRegistry.getOnlineUserIds(spaceIdForWs);
                            for (Long uid : onlineUserIds) {
                                collabSessionRegistry.disconnectUserInSpaces(uid, spaceIdForWs, "space_deleted", "空间已被删除");
                            }
                            collabSessionRegistry.clearAllPictureStates(spaceIdForWs);
                            log.info("[SpaceService] adminDelete 事务提交后断 WS + 清 pictureStates: spaceId={}, affectedUsers={}", spaceIdForWs, onlineUserIds.size());
                        } catch (Exception e) {
                            log.warn("[SpaceService] adminDelete 事务提交后断 WS 失败(需关注): spaceId={}", spaceIdForWs, e);
                        }
                    }
                });

        // 级联删除 picture_share 记录
        // PictureShare 实体没有 spaceId 字段,只能按 pictureId 逐个删
        try {
            int shareDeleted = 0;
            for (Picture pic : pictures) {
                shareDeleted += pictureShareMapper.delete(
                        new LambdaQueryWrapper<PictureShare>()
                                .eq(PictureShare::getPictureId, pic.getId()));
            }
            log.info("[SpaceService] adminDelete 级联删 picture_share: spaceId={}, count={}", id, shareDeleted);
        } catch (Exception e) {
            log.warn("[SpaceService] adminDelete 删 picture_share 失败(需关注): spaceId={}", id, e);
        }

        // 2. 级联删除团队成员
        spaceTeamMemberMapper.delete(new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getSpaceId, id));
        // 3. 删除图片记录（物理文件已由 file_resource 引用计数管理）
        pictureService.remove(new LambdaQueryWrapper<Picture>().eq(Picture::getSpaceId, id));
        int result = baseMapper.deleteById(id);
        ExcUtils.throwIfTrue(result <= 0, ExceptionCode.DATABASE_ERROR, "删除失败");
        return true;
    }

    @Override
    public Boolean adminSetStatus(Long id, Integer status) {
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        ExcUtils.throwIfTrue(status == null || (status != 0 && status != 1), ExceptionCode.PARAMETER_ERROR, "无效的状态值，仅允许0或1");
        Space space = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");
        space.setStatus(status);
        boolean result = this.updateById(space);
        ExcUtils.throwIfTrue(!result, ExceptionCode.DATABASE_ERROR, "更新失败");

        // 空间被禁用时，清掉该空间所有在线用户的 WS 状态
        if (Integer.valueOf(0).equals(status)) {
            try {
                java.util.Set<Long> onlineUserIds = collabSessionRegistry.getOnlineUserIds(id);
                for (Long uid : onlineUserIds) {
                    collabSessionRegistry.disconnectUserInSpaces(uid, id, "space_disabled", "空间已被禁用");
                }
                collabSessionRegistry.clearAllPictureStates(id);
                log.info("[SpaceService] adminSetStatus 禁用空间后断 WS: spaceId={}, affectedUsers={}", id, onlineUserIds.size());
            } catch (Exception e) {
                log.warn("[SpaceService] adminSetStatus 断 WS 失败(需关注): spaceId={}", id, e);
            }
        }
        return true;
    }

    @Override
    public List<SpaceMemberVO> teamMemberList(Long spaceId) {
        ExcUtils.throwIfTrue(spaceId == null, ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        User user = UserHolder.getUser();

        Space space = baseMapper.selectById(spaceId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");
        ExcUtils.throwIfTrue(!Integer.valueOf(1).equals(space.getType()), ExceptionCode.PARAMETER_ERROR, "非团队空间");
        validateSpaceActive(space);

        boolean isCreator = Objects.equals(space.getUserId(), user.getId());
        boolean isTeamMember = spaceTeamMemberMapper.selectCount(
                new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getSpaceId, spaceId).eq(SpaceTeamMember::getUserId, user.getId())) > 0;
        ExcUtils.throwIfTrue(!isCreator && !isTeamMember, ExceptionCode.FORBIDDEN, "无权限访问该空间");

        List<SpaceTeamMember> teamMembers = spaceTeamMemberMapper.selectList(
                new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getSpaceId, spaceId));
        if (CollUtil.isEmpty(teamMembers)) {
            return new ArrayList<>();
        }

        Set<Long> userIds = teamMembers.stream().map(SpaceTeamMember::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectByIds(userIds)
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        List<Long> roleIds = teamMembers.stream().map(tm -> tm.getRoleId() != null ? tm.getRoleId().longValue() : null).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> roleNameMap = new HashMap<>();
        for (Long roleId : roleIds) {
            roleNameMap.put(roleId, ROLE_NAME_MAP.getOrDefault(roleId.intValue(), "未知角色"));
        }

        return teamMembers.stream().map(tm -> {
            User u = userMap.get(tm.getUserId());
            if (u == null) return null;
            return new SpaceMemberVO(u.getId(), u.getNickname(), u.getAvatar(),
                    tm.getRoleId() != null ? tm.getRoleId().longValue() : null,
                    roleNameMap.getOrDefault(tm.getRoleId() != null ? tm.getRoleId().longValue() : null, ""));
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * 邀请成员加入团队空间
     * 校验链：空间存在 → 操作者有邀请权限 → 目标用户存在 → 角色合法 → 防越权提升 → 防重复邀请
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean teamInvite(TeamInviteRequest request) {
        Long spaceId = request.getSpaceId();
        Long userId = request.getUserId();
        Long roleId = request.getRoleId();
        ExcUtils.throwIfTrue(spaceId == null || userId == null || roleId == null, ExceptionCode.PARAMETER_ERROR, "参数不能为空");

        User operator = UserHolder.getUser();

        Space space = baseMapper.selectById(spaceId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");
        ExcUtils.throwIfTrue(!Integer.valueOf(1).equals(space.getType()), ExceptionCode.PARAMETER_ERROR, "非团队空间");
        validateSpaceActive(space);

        // 空间创建者或 team OWNER 才能邀请
        boolean isCreator = Objects.equals(operator.getId(), space.getUserId());
        boolean isTeamOwner = !isCreator && isTeamOwner(space.getId(), operator.getId());
        ExcUtils.throwIfTrue(!isCreator && !isTeamOwner,
                ExceptionCode.FORBIDDEN, "仅空间创建者或团队所有者可邀请成员");

        User targetUser = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(targetUser), ExceptionCode.PARAMETER_ERROR, "目标用户不存在");
        // 不能邀请已禁用的用户
        ExcUtils.throwIfTrue(targetUser.getStatus() == null || !Integer.valueOf(1).equals(targetUser.getStatus()),
                ExceptionCode.FORBIDDEN, "不能邀请已禁用的用户");

        // 验证角色ID（仅允许 1=所有者, 2=成员）
        ExcUtils.throwIfTrue(roleId != 1 && roleId != 2,
                ExceptionCode.PARAMETER_ERROR, "无效的团队角色，仅允许 1（所有者）或 2（成员）");

        // 仅空间创建者可授予所有者角色，防止权限提升
        if (roleId == 1) {
            ExcUtils.throwIfTrue(!Objects.equals(space.getUserId(), operator.getId()),
                    ExceptionCode.FORBIDDEN, "仅空间创建者可授予所有者角色");
        }

        // 检查是否已是成员，防止重复邀请（避免静默覆盖角色）
        Long existingCount = spaceTeamMemberMapper.selectCount(
                new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getSpaceId, spaceId).eq(SpaceTeamMember::getUserId, userId));
        ExcUtils.throwIfTrue(existingCount > 0, ExceptionCode.PARAMETER_ERROR, "该用户已是团队成员");

        // 直接插入团队成员记录
        SpaceTeamMember teamMember = new SpaceTeamMember();
        teamMember.setSpaceId(spaceId);
        teamMember.setUserId(userId);
        teamMember.setRoleId(roleId.intValue());
        spaceTeamMemberMapper.insert(teamMember);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean teamRemove(TeamRemoveRequest request) {
        Long spaceId = request.getSpaceId();
        Long userId = request.getUserId();
        ExcUtils.throwIfTrue(spaceId == null || userId == null, ExceptionCode.PARAMETER_ERROR, "参数不能为空");

        User operator = UserHolder.getUser();

        Space space = baseMapper.selectById(spaceId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");
        ExcUtils.throwIfTrue(!Integer.valueOf(1).equals(space.getType()), ExceptionCode.PARAMETER_ERROR, "非团队空间");
        validateSpaceActive(space);

        // 空间创建者或 team OWNER 才能移除
        boolean isCreator = Objects.equals(operator.getId(), space.getUserId());
        boolean isTeamOwner = !isCreator && isTeamOwner(space.getId(), operator.getId());
        ExcUtils.throwIfTrue(!isCreator && !isTeamOwner,
                ExceptionCode.FORBIDDEN, "仅空间创建者或团队所有者可移除成员");

        ExcUtils.throwIfTrue(Objects.equals(space.getUserId(), userId), ExceptionCode.PARAMETER_ERROR, "不能移除空间创建者");
        ExcUtils.throwIfTrue(Objects.equals(operator.getId(), userId), ExceptionCode.PARAMETER_ERROR, "不能移除自己");

        Long count = spaceTeamMemberMapper.selectCount(
                new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getSpaceId, spaceId).eq(SpaceTeamMember::getUserId, userId));
        ExcUtils.throwIfTrue(count == 0, ExceptionCode.PARAMETER_ERROR, "该用户不是团队成员");

        spaceTeamMemberMapper.delete(
                new LambdaQueryWrapper<SpaceTeamMember>()
                        .eq(SpaceTeamMember::getSpaceId, spaceId)
                        .eq(SpaceTeamMember::getUserId, userId));

        // 被移除后强制断开该用户在该空间的 WS 连接
        // WS 断开是不可回滚操作，移到事务提交后执行
        final Long removedUserId = userId;
        final Long removedSpaceId = spaceId;
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            // 用 disconnectUserInSpaces 只断被移除的那一个 space（不限整个用户的所有空间）
                            java.util.Set<Long> affected = collabSessionRegistry.disconnectUserInSpaces(
                                    removedUserId, removedSpaceId, "team_removed", "您已被移出团队空间");
                            log.info("[SpaceService] 团队成员移除后(事务提交后)强制断 WS: removedUser={}, spaceId={}, affectedSpaces={}",
                                    removedUserId, removedSpaceId, affected);
                        } catch (Exception e) {
                            log.warn("[SpaceService] 团队成员移除后续 WS 清理失败: removedUser={}, spaceId={}",
                                    removedUserId, removedSpaceId, e);
                        }
                    }
                });
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean teamChangeRole(TeamChangeRoleRequest request) {
        Long spaceId = request.getSpaceId();
        Long userId = request.getUserId();
        Long roleId = request.getRoleId();
        ExcUtils.throwIfTrue(spaceId == null || userId == null || roleId == null, ExceptionCode.PARAMETER_ERROR, "参数不能为空");

        User operator = UserHolder.getUser();

        Space space = baseMapper.selectById(spaceId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");
        ExcUtils.throwIfTrue(!Integer.valueOf(1).equals(space.getType()), ExceptionCode.PARAMETER_ERROR, "非团队空间");
        validateSpaceActive(space);

        // 空间创建者或 team OWNER 才能变更角色
        boolean isCreator = Objects.equals(operator.getId(), space.getUserId());
        boolean isTeamOwner = !isCreator && isTeamOwner(space.getId(), operator.getId());
        ExcUtils.throwIfTrue(!isCreator && !isTeamOwner,
                ExceptionCode.FORBIDDEN, "仅空间创建者或团队所有者可变更成员角色");

        Long count = spaceTeamMemberMapper.selectCount(
                new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getSpaceId, spaceId).eq(SpaceTeamMember::getUserId, userId));
        ExcUtils.throwIfTrue(count == 0, ExceptionCode.PARAMETER_ERROR, "该用户不是团队成员");

        // 不能变更空间创建者的角色
        ExcUtils.throwIfTrue(Objects.equals(space.getUserId(), userId),
                ExceptionCode.PARAMETER_ERROR, "不能变更空间创建者的角色");

        // 验证角色ID（仅允许 1=所有者, 2=成员）
        ExcUtils.throwIfTrue(roleId != 1 && roleId != 2,
                ExceptionCode.PARAMETER_ERROR, "无效的团队角色，仅允许 1（所有者）或 2（成员）");

        // 仅空间创建者可授予所有者角色，防止越权提升
        if (roleId == 1) {
            ExcUtils.throwIfTrue(!Objects.equals(space.getUserId(), operator.getId()),
                    ExceptionCode.FORBIDDEN, "仅空间创建者可授予所有者角色");
        }

        // 查找并更新已有成员记录
        SpaceTeamMember existing = spaceTeamMemberMapper.selectOne(
                new LambdaQueryWrapper<SpaceTeamMember>()
                        .eq(SpaceTeamMember::getSpaceId, spaceId)
                        .eq(SpaceTeamMember::getUserId, userId));
        ExcUtils.throwIfTrue(existing == null, ExceptionCode.PARAMETER_ERROR, "该用户不是团队成员");
        existing.setRoleId(roleId.intValue());
        spaceTeamMemberMapper.updateById(existing);
        return true;
    }

    /**
     * 获取用户可上传图片的空间列表
     * 包括：私人空间 + 有上传权限的团队空间（角色为所有者或成员）
     */
    @Override
    public List<SpaceVO> saveableSpaces() {
        User user = UserHolder.getUser();
        Long userId = user.getId();

        List<SpaceVO> result = new ArrayList<>();

        // 1. 私人空间（每人最多一个，仅返回正常状态）
        Space privateSpace = baseMapper.selectOne(
                new LambdaQueryWrapper<Space>().eq(Space::getUserId, userId).eq(Space::getType, 0).eq(Space::getStatus, 1).last("LIMIT 1"));
        if (privateSpace != null) {
            SpaceVO vo = new SpaceVO();
            BeanUtil.copyProperties(privateSpace, vo);
            result.add(vo);
        }

        // 2. 有上传权限的团队空间（roleId=1 所有者, 2 成员）
        List<SpaceTeamMember> memberships = spaceTeamMemberMapper.selectList(
                new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getUserId, userId)
                        .in(SpaceTeamMember::getRoleId, List.of(1, 2)));
        if (!memberships.isEmpty()) {
            Set<Long> teamSpaceIds = memberships.stream()
                    .map(SpaceTeamMember::getSpaceId)
                    .collect(Collectors.toSet());
            // 排除用户作为创建者的私人空间（已包含），仅返回正常状态
            List<Space> teamSpaces = baseMapper.selectList(
                    new LambdaQueryWrapper<Space>().in(Space::getId, teamSpaceIds).eq(Space::getStatus, 1));
            for (Space space : teamSpaces) {
                SpaceVO vo = new SpaceVO();
                BeanUtil.copyProperties(space, vo);
                result.add(vo);
            }
        }

        return result;
    }

    /**
     * 根据用户等级获取私人空间存储配额
     */
    private Long getPrivateStorageSize(int level) {
        return switch (level) {
            case 1 -> VIP_STORAGE_SIZE;
            case 2, 3 -> SVIP_STORAGE_SIZE;
            default -> DEFAULT_STORAGE_SIZE;
        };
    }

    /**
     * 根据用户等级获取团队空间存储配额
     */
    private Long getTeamStorageSize(int level) {
        return switch (level) {
            case 1 -> TEAM_VIP_STORAGE_SIZE;
            case 2, 3 -> TEAM_SVIP_STORAGE_SIZE;
            default -> TEAM_DEFAULT_STORAGE_SIZE;
        };
    }

    /**
     * 判断 user 在 space 是不是 team OWNER(roleId=1)
     */
    private boolean isTeamOwner(Long spaceId, Long userId) {
        SpaceTeamMember m = spaceTeamMemberMapper.selectOne(
                new LambdaQueryWrapper<SpaceTeamMember>()
                        .eq(SpaceTeamMember::getSpaceId, spaceId)
                        .eq(SpaceTeamMember::getUserId, userId)
                        .eq(SpaceTeamMember::getRoleId, 1));
        return m != null;
    }

    private void validateSpaceActive(Space space) {
        Space.validateActive(space);
    }

    /**
     * 构建 SpaceVO（消除 listSpace/getSpace/adminList 三处重复的 VO 填充逻辑）
     *
     * @param space           空间实体
     * @param userMap         userId → User 映射
     * @param pictureCountMap spaceId → 图片数量映射
     * @param teamMembers     团队成员列表，null 表示不设置团队成员
     * @param maxMembers      团队成员最大返回数，0 表示不限
     */
    private SpaceVO buildSpaceVO(Space space, Map<Long, User> userMap,
                                  Map<Long, Long> pictureCountMap,
                                  List<SpaceTeamMember> teamMembers, int maxMembers) {
        SpaceVO vo = new SpaceVO();
        BeanUtil.copyProperties(space, vo);
        vo.setPictureCount(pictureCountMap.getOrDefault(space.getId(), 0L));
        User creator = userMap.get(space.getUserId());
        if (creator != null) {
            vo.setUserName(creator.getNickname());
            vo.setUserAvatar(creator.getAvatar());
        }
        if (teamMembers != null && !teamMembers.isEmpty()) {
            vo.setTeamMembers(buildTeamMemberVOs(teamMembers, userMap, maxMembers));
        }
        return vo;
    }

    /**
     * 将团队成员列表转换为 SpaceMemberVO 列表（消除 listSpace/getSpace/adminList 三处重复）
     *
     * @param teamMembers 该空间的团队成员列表
     * @param userMap     userId → User 映射
     * @param maxMembers  最多返回的成员数，传 0 或负数表示不限制
     */
    private List<SpaceMemberVO> buildTeamMemberVOs(List<SpaceTeamMember> teamMembers, Map<Long, User> userMap, int maxMembers) {
        Map<Long, Long> userIdRoleIdMap = teamMembers.stream()
                .collect(Collectors.toMap(SpaceTeamMember::getUserId,
                        tm -> tm.getRoleId() != null ? tm.getRoleId().longValue() : 0L, (a, b) -> a));
        List<Long> roleIds = teamMembers.stream()
                .map(tm -> tm.getRoleId() != null ? tm.getRoleId().longValue() : null)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, String> roleNameMap = new HashMap<>();
        for (Long roleId : roleIds) {
            roleNameMap.put(roleId, ROLE_NAME_MAP.getOrDefault(roleId.intValue(), "未知角色"));
        }
        java.util.stream.Stream<SpaceTeamMember> stream = teamMembers.stream();
        if (maxMembers > 0) {
            stream = stream.limit(maxMembers);
        }
        return stream
                .map(SpaceTeamMember::getUserId)
                .map(userMap::get)
                .filter(Objects::nonNull)
                .map(m -> {
                    Long roleId = userIdRoleIdMap.get(m.getId());
                    return new SpaceMemberVO(m.getId(), m.getNickname(), m.getAvatar(), roleId, roleNameMap.getOrDefault(roleId, ""));
                })
                .collect(Collectors.toList());
    }
}
