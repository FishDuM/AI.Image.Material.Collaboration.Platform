package hk.ljx.fishpicsbackend.space.component;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.collab.CollabSessionRegistry;
import hk.ljx.fishpicsbackend.common.cache.RedisCacheManager;
import hk.ljx.fishpicsbackend.common.constants.SpaceConstants;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.space.dto.TeamChangeRoleRequest;
import hk.ljx.fishpicsbackend.space.dto.TeamInviteRequest;
import hk.ljx.fishpicsbackend.space.dto.TeamRemoveRequest;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.space.enums.TeamMemberRole;
import hk.ljx.fishpicsbackend.space.vo.SpaceMemberVO;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
public class SpaceTeamMemberManager {

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisCacheManager cacheManager;

    @Resource
    private CollabSessionRegistry collabSessionRegistry;

    @Resource
    private SpacePermissionChecker spacePermissionChecker;

    @Resource
    private SpaceAccessResolver spaceAccessResolver;

    public boolean isTeamOwner(Long spaceId, Long userId) {
        return spacePermissionChecker.isTeamOwner(spaceId, userId);
    }

    public List<SpaceMemberVO> listMembers(Long spaceId) {
        ExcUtils.throwIfTrue(spaceId == null, ExceptionCode.PARAMETER_ERROR, "空间ID不能为空");
        Space space = resolveSpaceAccess(spaceId);
        ensureTeamSpace(space);

        List<SpaceTeamMember> teamMembers = spaceTeamMemberMapper.selectList(
                new LambdaQueryWrapper<SpaceTeamMember>().eq(SpaceTeamMember::getSpaceId, spaceId));
        if (CollUtil.isEmpty(teamMembers)) {
            return new ArrayList<>();
        }

        Set<Long> userIds = teamMembers.stream().map(SpaceTeamMember::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectByIds(userIds)
                .stream().collect(Collectors.toMap(User::getId, user -> user));
        Map<Long, String> roleNameMap = buildRoleNameMap(teamMembers);

        return teamMembers.stream()
                .map(member -> toMemberVO(member, userMap, roleNameMap))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public Boolean invite(TeamInviteRequest request) {
        Long spaceId = request.getSpaceId();
        Long userId = request.getUserId();
        Long roleId = request.getRoleId();
        ExcUtils.throwIfTrue(spaceId == null || userId == null || roleId == null,
                ExceptionCode.PARAMETER_ERROR, "参数不能为空");

        User operator = LoginContextHelper.requireUser();
        Space space = resolveSpaceAccess(spaceId);
        ensureTeamSpace(space);
        ensureCanManage(space, operator);

        User targetUser = userMapper.selectById(userId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(targetUser), ExceptionCode.PARAMETER_ERROR, "目标用户不存在");
        ExcUtils.throwIfTrue(!targetUser.isActive(),
                ExceptionCode.FORBIDDEN, "不能邀请已禁用的用户");

        validateRole(roleId);
        ensureCanGrantOwner(space, operator, roleId);

        Long existingCount = spaceTeamMemberMapper.selectCount(
                new LambdaQueryWrapper<SpaceTeamMember>()
                        .eq(SpaceTeamMember::getSpaceId, spaceId)
                        .eq(SpaceTeamMember::getUserId, userId));
        ExcUtils.throwIfTrue(existingCount > 0, ExceptionCode.PARAMETER_ERROR, "该用户已经是团队成员");

        SpaceTeamMember teamMember = new SpaceTeamMember();
        teamMember.setSpaceId(spaceId);
        teamMember.setUserId(userId);
        teamMember.setRoleId(roleId.intValue());
        spaceTeamMemberMapper.insert(teamMember);
        evictUserPermCacheAfterCommit(userId);
        return true;
    }

    public Boolean remove(TeamRemoveRequest request) {
        Long spaceId = request.getSpaceId();
        Long userId = request.getUserId();
        ExcUtils.throwIfTrue(spaceId == null || userId == null, ExceptionCode.PARAMETER_ERROR, "参数不能为空");

        User operator = LoginContextHelper.requireUser();
        Space space = resolveSpaceAccess(spaceId);
        ensureTeamSpace(space);
        ensureCanManage(space, operator);

        ExcUtils.throwIfTrue(Objects.equals(space.getUserId(), userId), ExceptionCode.PARAMETER_ERROR, "不能移除空间创建者");
        ExcUtils.throwIfTrue(Objects.equals(operator.getId(), userId), ExceptionCode.PARAMETER_ERROR, "不能移除自己");
        ensureMemberExists(spaceId, userId);

        spaceTeamMemberMapper.delete(
                new LambdaQueryWrapper<SpaceTeamMember>()
                        .eq(SpaceTeamMember::getSpaceId, spaceId)
                        .eq(SpaceTeamMember::getUserId, userId));
        evictUserPermCacheAfterCommit(userId);
        disconnectUserAfterCommit(userId, spaceId);
        return true;
    }

    public Boolean changeRole(TeamChangeRoleRequest request) {
        Long spaceId = request.getSpaceId();
        Long userId = request.getUserId();
        Long roleId = request.getRoleId();
        ExcUtils.throwIfTrue(spaceId == null || userId == null || roleId == null,
                ExceptionCode.PARAMETER_ERROR, "参数不能为空");

        User operator = LoginContextHelper.requireUser();
        Space space = resolveSpaceAccess(spaceId);
        ensureTeamSpace(space);
        ensureCanManage(space, operator);
        ensureMemberExists(spaceId, userId);

        ExcUtils.throwIfTrue(Objects.equals(space.getUserId(), userId),
                ExceptionCode.PARAMETER_ERROR, "不能变更空间创建者的角色");
        validateRole(roleId);
        ensureCanGrantOwner(space, operator, roleId);

        SpaceTeamMember existing = spaceTeamMemberMapper.selectOne(
                new LambdaQueryWrapper<SpaceTeamMember>()
                        .eq(SpaceTeamMember::getSpaceId, spaceId)
                        .eq(SpaceTeamMember::getUserId, userId));
        ExcUtils.throwIfTrue(existing == null, ExceptionCode.PARAMETER_ERROR, "该用户不是团队成员");
        existing.setRoleId(roleId.intValue());
        spaceTeamMemberMapper.updateById(existing);
        evictUserPermCacheAfterCommit(userId);
        return true;
    }

    private Space resolveSpaceAccess(Long spaceId) {
        return spaceAccessResolver.resolve(spaceId);
    }

    private void ensureTeamSpace(Space space) {
        ExcUtils.throwIfTrue(!ExcUtils.eq(space.getType(), SpaceConstants.SPACE_TYPE_TEAM),
                ExceptionCode.PARAMETER_ERROR, "非团队空间");
    }

    private void ensureCanManage(Space space, User operator) {
        spacePermissionChecker.checkManageMembers(space, operator);
    }

    private void validateRole(Long roleId) {
        ExcUtils.throwIfTrue(!TeamMemberRole.isGrantable(roleId),
                ExceptionCode.PARAMETER_ERROR, "无效的团队角色");
    }

    private void ensureCanGrantOwner(Space space, User operator, Long roleId) {
        if (TeamMemberRole.isOwner(roleId)) {
            ExcUtils.throwIfTrue(!Objects.equals(space.getUserId(), operator.getId()),
                    ExceptionCode.FORBIDDEN, "仅空间创建者可授予所有者角色");
        }
    }

    private void ensureMemberExists(Long spaceId, Long userId) {
        Long count = spaceTeamMemberMapper.selectCount(
                new LambdaQueryWrapper<SpaceTeamMember>()
                        .eq(SpaceTeamMember::getSpaceId, spaceId)
                        .eq(SpaceTeamMember::getUserId, userId));
        ExcUtils.throwIfTrue(count == 0, ExceptionCode.PARAMETER_ERROR, "该用户不是团队成员");
    }

    private Map<Long, String> buildRoleNameMap(List<SpaceTeamMember> teamMembers) {
        List<Long> roleIds = teamMembers.stream()
                .map(member -> member.getRoleId() != null ? member.getRoleId().longValue() : null)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> roleNameMap = new HashMap<>();
        for (Long roleId : roleIds) {
            roleNameMap.put(roleId, TeamMemberRole.nameOf(roleId.intValue()));
        }
        return roleNameMap;
    }

    private SpaceMemberVO toMemberVO(SpaceTeamMember member, Map<Long, User> userMap, Map<Long, String> roleNameMap) {
        User user = userMap.get(member.getUserId());
        if (user == null) {
            return null;
        }
        Long roleId = member.getRoleId() != null ? member.getRoleId().longValue() : null;
        return new SpaceMemberVO(user.getId(), user.getNickname(), user.getAvatar(),
                roleId, roleNameMap.getOrDefault(roleId, ""));
    }

    private void evictUserPermCacheAfterCommit(Long userId) {
        cacheManager.evictUserPermCacheAfterCommit(userId);
    }

    private void disconnectUserAfterCommit(Long userId, Long spaceId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    boolean disconnected = collabSessionRegistry.disconnectUserInSpace(
                            userId, spaceId, "您已被移出团队空间");
                    log.info("[SpaceTeamMemberManager] team member removed, ws disconnected: user={}, spaceId={}, disconnected={}",
                            userId, spaceId, disconnected);
                } catch (Exception e) {
                    log.warn("[SpaceTeamMemberManager] failed to disconnect removed member: user={}, spaceId={}",
                            userId, spaceId, e);
                }
            }
        });
    }
}
