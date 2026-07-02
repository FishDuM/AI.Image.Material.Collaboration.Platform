package hk.ljx.fishpicsbackend.space.service.impl;
import hk.ljx.fishpicsbackend.space.entity.Space;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.cache.RedisCacheManager;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.infra.DistributedLockService;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.common.utils.XssSanitizer;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import hk.ljx.fishpicsbackend.space.dto.CreateSpaceRequest;
import hk.ljx.fishpicsbackend.space.dto.SpaceAdminUpdateRequest;
import hk.ljx.fishpicsbackend.space.dto.SpacePictureListRequest;
import hk.ljx.fishpicsbackend.space.dto.SpaceQueryWrapper;
import hk.ljx.fishpicsbackend.space.dto.UpdateSpaceRequest;
import hk.ljx.fishpicsbackend.space.component.SpaceAccessResolver;
import hk.ljx.fishpicsbackend.space.component.SpaceAdminManager;
import hk.ljx.fishpicsbackend.space.component.SpacePermissionChecker;
import hk.ljx.fishpicsbackend.space.component.SpaceTeamMemberManager;
import hk.ljx.fishpicsbackend.space.component.SpaceVOAssembler;
import hk.ljx.fishpicsbackend.space.enums.TeamMemberRole;
import hk.ljx.fishpicsbackend.space.service.SpaceService;
import hk.ljx.fishpicsbackend.space.vo.SpaceMemberVO;
import hk.ljx.fishpicsbackend.space.vo.SpaceVO;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.space.dto.TeamInviteRequest;
import hk.ljx.fishpicsbackend.space.dto.TeamRemoveRequest;
import hk.ljx.fishpicsbackend.space.dto.TeamChangeRoleRequest;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.Resource;

import java.util.*;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.SpaceConstants.*;

@Slf4j
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
        implements SpaceService {

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    @Resource
    private SpaceAccessResolver spaceAccessResolver;

    @Resource
    private SpaceAdminManager spaceAdminManager;

    @Resource
    private SpaceTeamMemberManager teamMemberManager;

    @Resource
    private SpacePermissionChecker spacePermissionChecker;

    @Resource
    private SpaceVOAssembler spaceVOAssembler;

    @Resource
    private DistributedLockService distributedLockService;

    @Resource
    private RedisCacheManager cacheManager;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean createSpace(CreateSpaceRequest createSpace, User user) {
        String name = createSpace.getName();
        String introduction = createSpace.getIntroduction();
        Integer type = createSpace.getType();
        ExcUtils.throwIfTrue(name == null || type == null, "空间名称不能为空");
        ExcUtils.throwIfTrue(!ExcUtils.eq(type, SPACE_TYPE_PRIVATE)
                && !ExcUtils.eq(type, SPACE_TYPE_TEAM), "空间类型不合法，仅支持 0（私人空间）或 1（团队空间）");
        ExcUtils.throwIfTrue(user == null || user.getId() == null, "用户不存在");

        Integer level = user.getLevel() != null ? user.getLevel() : 0;

        String privateLockKey = ExcUtils.eq(type, SPACE_TYPE_PRIVATE)
                ? "LOCK:SPACE:CREATE:PRIVATE:" + user.getId() : null;
        String teamLockKey = ExcUtils.eq(type, SPACE_TYPE_TEAM)
                ? "LOCK:SPACE:CREATE:TEAM:" + user.getId() : null;
        if (privateLockKey != null && !distributedLockService.tryLock(privateLockKey)) {
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS, "其他请求正在创建您的私人空间,请稍后再试");
        }
        if (teamLockKey != null && !distributedLockService.tryLock(teamLockKey)) {
            if (privateLockKey != null) {
                try { distributedLockService.unlock(privateLockKey); } catch (Exception e) { log.warn("释放私有空间创建锁失败: {}", e.getMessage()); }
            }
            throw new BaseException(ExceptionCode.TOO_MANY_REQUESTS, "其他请求正在创建团队空间,请稍后再试");
        }
        try {
        List<Space> spaceList = baseMapper
                .selectList(new LambdaQueryWrapper<Space>().eq(Space::getUserId, user.getId()).eq(Space::getType, type));
        if (ExcUtils.eq(type, SPACE_TYPE_PRIVATE)) {
            // 私人空间：每人限一个(锁内二次校验,防止锁内其他事务并入)
            ExcUtils.throwIfTrue(!spaceList.isEmpty(), "私人空间已存在");
        } else if (ExcUtils.eq(type, SPACE_TYPE_TEAM)) {
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
        if (ExcUtils.eq(type, SPACE_TYPE_PRIVATE)) {
            // 私人空间
            space.setStorageSize(getPrivateStorageSize(level));
        } else if (ExcUtils.eq(type, SPACE_TYPE_TEAM)) {
            // 团队空间
            space.setStorageSize(getTeamStorageSize(level));
        }
        space.setName(XssSanitizer.clean(name));
        space.setIntroduction(XssSanitizer.cleanRelaxed(introduction));
        space.setType(type);
        space.setUserId(user.getId());
        int insert = baseMapper.insert(space);
        ExcUtils.throwIfTrue(insert <= 0, "创建空间失败");
        if (ExcUtils.eq(type, SPACE_TYPE_TEAM)) {
            SpaceTeamMember teamMember = new SpaceTeamMember();
            teamMember.setSpaceId(space.getId());
            teamMember.setUserId(user.getId());
            teamMember.setRoleId(TeamMemberRole.OWNER.code());
            spaceTeamMemberMapper.insert(teamMember);
            evictUserPermCacheAfterCommit(user.getId());
        }
        // 事务提交后再释放锁，防止其他线程在事务未提交时读到旧数据
        String finalPrivateLockKey = privateLockKey;
        String finalTeamLockKey = teamLockKey;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                if (finalPrivateLockKey != null) {
                    try { distributedLockService.unlock(finalPrivateLockKey); } catch (Exception e) { log.warn("释放私有空间创建锁失败: {}", e.getMessage()); }
                }
                if (finalTeamLockKey != null) {
                    try { distributedLockService.unlock(finalTeamLockKey); } catch (Exception e) { log.warn("释放团队空间创建锁失败: {}", e.getMessage()); }
                }
            }
        });
        return true;
        } catch (Exception e) {
            // 异常时事务会回滚，也需要释放锁
            if (privateLockKey != null) {
                try { distributedLockService.unlock(privateLockKey); } catch (Exception ex) { log.warn("释放私有空间创建锁失败: {}", ex.getMessage()); }
            }
            if (teamLockKey != null) {
                try { distributedLockService.unlock(teamLockKey); } catch (Exception ex) { log.warn("释放团队空间创建锁失败: {}", ex.getMessage()); }
            }
            throw e;
        }
    }

    @Override
    public List<SpaceVO> listSpace(Integer type) {
        User user = LoginContextHelper.requireUser();
        ExcUtils.throwIfTrue(type == null, ExceptionCode.PARAMETER_ERROR, "空间类型不能为空");
        Long userId = user.getId();

        LambdaQueryWrapper<Space> queryWrapper = new LambdaQueryWrapper<>();
        if (ExcUtils.eq(type, SPACE_TYPE_TEAM)) {
            List<Long> memberSpaceIds = spaceTeamMemberMapper.selectList(
                    new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getUserId, userId))
                    .stream().map(SpaceTeamMember::getSpaceId).collect(Collectors.toList());
            if (memberSpaceIds.isEmpty()) {
                queryWrapper.eq(Space::getUserId, userId)
                        .eq(Space::getType, type)
                        .eq(Space::getStatus, SPACE_STATUS_ENABLED);
            } else {
                queryWrapper.and(w -> w.eq(Space::getUserId, userId).or().in(Space::getId, memberSpaceIds))
                        .eq(Space::getType, type)
                        .eq(Space::getStatus, SPACE_STATUS_ENABLED);
            }
        } else {
            queryWrapper.eq(Space::getUserId, userId)
                    .eq(Space::getType, type)
                    .eq(Space::getStatus, SPACE_STATUS_ENABLED);
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
        // 批量查询所有空间的成员
        List<Long> spaceIds = spaceList.stream().map(Space::getId).collect(Collectors.toList());
        Map<Long, List<SpaceTeamMember>> membersBySpaceId = new HashMap<>();
        if (ExcUtils.eq(type, SPACE_TYPE_TEAM) && !spaceIds.isEmpty()) {
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

        Map<Long, Long> pictureCountMap = spaceVOAssembler.pictureCountMap(spaceIds);

        List<SpaceVO> voList = new ArrayList<>();
        for (Space space : spaceList) {
            List<SpaceTeamMember> teamMembers = ExcUtils.eq(type, SPACE_TYPE_TEAM)
                    ? membersBySpaceId.getOrDefault(space.getId(), Collections.emptyList())
                    : null;
            voList.add(spaceVOAssembler.build(space, userMap, pictureCountMap, teamMembers,
                    SpaceVOAssembler.MAX_TEAM_MEMBER_DISPLAY));
        }
        return voList;
    }

    @Override
    public SpaceVO getSpace(Long id) {
        ExcUtils.throwIfTrue(id == null, ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        User user = LoginContextHelper.requireUser();
        Long userId = user.getId();

        // 先查 DB 做权限校验，再读缓存——确保缓存命中也不会绕过权限检查
        Space space = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");
        Space.validateActive(space);
        spacePermissionChecker.checkAccess(space, userId);

        SpaceVO cached = cacheManager.getSpaceDetailCache().get(String.valueOf(id), SpaceVO.class);
        if (cached != null) {
            return cached;
        }

        List<SpaceTeamMember> teamMembers = Collections.emptyList();
        if (spacePermissionChecker.isTeamSpace(space)) {
            teamMembers = spaceTeamMemberMapper.selectList(new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getSpaceId, space.getId()));
        }

        Set<Long> userIds = new HashSet<>();
        if (space.getUserId() != null)
            userIds.add(space.getUserId());
        teamMembers.stream().map(SpaceTeamMember::getUserId).forEach(userIds::add);
        Map<Long, User> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMap = userMapper.selectByIds(userIds)
                    .stream().collect(Collectors.toMap(User::getId, u -> u));
        }

        long picCount = pictureMapper.selectCount(
                new LambdaQueryWrapper<Picture>().eq(Picture::getSpaceId, id));
        Map<Long, Long> pictureCountMap = Map.of(space.getId(), picCount);

        List<SpaceTeamMember> teamMembersForVO = spacePermissionChecker.isTeamSpace(space) && !teamMembers.isEmpty()
                ? teamMembers : null;
        SpaceVO vo = spaceVOAssembler.build(space, userMap, pictureCountMap, teamMembersForVO, 0);

        cacheManager.getSpaceDetailCache().put(String.valueOf(id), vo);
        return vo;
    }

    @Override
    public Boolean updateSpace(UpdateSpaceRequest updateSpace) {
        Long id = updateSpace.getId();
        String name = updateSpace.getName();
        String introduction = updateSpace.getIntroduction();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(id), ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(name), ExceptionCode.PARAMETER_ERROR, "空间名称不能为空");

        User user = LoginContextHelper.requireUser();
        Space space = baseMapper.selectById(id);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(space), ExceptionCode.PARAMETER_ERROR, "空间不存在");
        Space.validateActive(space);
        spacePermissionChecker.checkUpdateSpace(space, user);
        Space updateObj = new Space();
        updateObj.setId(id);
        updateObj.setName(XssSanitizer.clean(name));
        updateObj.setIntroduction(XssSanitizer.cleanRelaxed(introduction));
        updateObj.setVersion(space.getVersion());
        int update = baseMapper.updateById(updateObj);
        ExcUtils.throwIfTrue(update <= 0, ExceptionCode.PARAMETER_ERROR, "更新空间信息失败");
        cacheManager.getSpaceDetailCache().evict(String.valueOf(id));
        return true;
    }

    @Override
    public IPage<PictureVO> pictureList(SpacePictureListRequest spacePictureList) {
        Long spaceId = spacePictureList.getSpaceId();
        int current = spacePictureList.getCurrent();
        int pageSize = Math.min(Math.max(spacePictureList.getPageSize(), 1), 100);
        String sortField = spacePictureList.getSortField();
        String sortOrder = spacePictureList.getSortOrder();

        ExcUtils.throwIfTrue(spaceId == null, ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        Space space = resolveSpaceAccess(spaceId);
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
        Page<Picture> pictureList = pictureMapper.selectPage(picturePage, pictureQueryWrapper);
        Page<PictureVO> resultPage = new Page<>(pictureList.getCurrent(), pictureList.getSize(), pictureList.getTotal());
        resultPage.setPages(pictureList.getPages());
        resultPage.setRecords(pictureList.getRecords().stream()
                .map(picture -> PictureVO.ofUpload(picture.getId(), picture.getUrl()))
                .toList());
        return resultPage;
    }

    @Override
    public IPage<SpaceVO> adminList(SpaceQueryWrapper spaceQueryWrapper) {
        return spaceAdminManager.list(spaceQueryWrapper);
    }

    @Override
    public Boolean adminUpdate(SpaceAdminUpdateRequest request) {
        return spaceAdminManager.update(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean adminDelete(Long id) {
        return spaceAdminManager.delete(id);
    }

    @Override
    public Boolean adminSetStatus(Long id, Integer status) {
        return spaceAdminManager.setStatus(id, status);
    }

    @Override
    public List<SpaceMemberVO> teamMemberList(Long spaceId) {
        return teamMemberManager.listMembers(spaceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean teamInvite(TeamInviteRequest request) {
        return teamMemberManager.invite(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean teamRemove(TeamRemoveRequest request) {
        return teamMemberManager.remove(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean teamChangeRole(TeamChangeRoleRequest request) {
        return teamMemberManager.changeRole(request);
    }

    @Override
    public List<SpaceVO> saveableSpaces() {
        User user = LoginContextHelper.requireUser();
        Long userId = user.getId();

        List<SpaceVO> result = new ArrayList<>();

        Space privateSpace = baseMapper.selectOne(
                new LambdaQueryWrapper<Space>().eq(Space::getUserId, userId)
                        .eq(Space::getType, SPACE_TYPE_PRIVATE)
                        .eq(Space::getStatus, SPACE_STATUS_ENABLED)
                        .last("LIMIT 1"));
        if (privateSpace != null) {
            SpaceVO vo = new SpaceVO();
            BeanUtil.copyProperties(privateSpace, vo);
            result.add(vo);
        }

        // 有上传权限的团队空间（roleId=1 所有者, 2 成员）
        List<SpaceTeamMember> memberships = spaceTeamMemberMapper.selectList(
                new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getUserId, userId)
                        .in(SpaceTeamMember::getRoleId, TeamMemberRole.WRITABLE_ROLE_IDS));
        if (!memberships.isEmpty()) {
            Set<Long> teamSpaceIds = memberships.stream()
                    .map(SpaceTeamMember::getSpaceId)
                    .collect(Collectors.toSet());
            // 排除用户作为创建者的私人空间（已包含），仅返回正常状态
            List<Space> teamSpaces = baseMapper.selectList(
                    new LambdaQueryWrapper<Space>().in(Space::getId, teamSpaceIds)
                            .eq(Space::getStatus, SPACE_STATUS_ENABLED));
            for (Space space : teamSpaces) {
                SpaceVO vo = new SpaceVO();
                BeanUtil.copyProperties(space, vo);
                result.add(vo);
            }
        }

        return result;
    }

    private Long getPrivateStorageSize(int level) {
        return switch (level) {
            case 1 -> VIP_STORAGE_SIZE;
            case 2, 3 -> SVIP_STORAGE_SIZE;
            default -> DEFAULT_STORAGE_SIZE;
        };
    }

    private Long getTeamStorageSize(int level) {
        return switch (level) {
            case 1 -> TEAM_VIP_STORAGE_SIZE;
            case 2, 3 -> TEAM_SVIP_STORAGE_SIZE;
            default -> TEAM_DEFAULT_STORAGE_SIZE;
        };
    }

    @Override
    public Space resolveSpaceAccess(Long spaceId) {
        return spaceAccessResolver.resolve(spaceId);
    }


    private void evictUserPermCacheAfterCommit(Long userId) {
        cacheManager.evictUserPermCacheAfterCommit(userId);
    }



}
