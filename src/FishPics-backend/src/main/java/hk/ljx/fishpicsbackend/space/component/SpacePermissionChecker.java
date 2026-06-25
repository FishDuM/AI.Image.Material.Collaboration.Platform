package hk.ljx.fishpicsbackend.space.component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.common.cache.RedisCacheManager;
import hk.ljx.fishpicsbackend.common.enums.Role;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.space.enums.TeamMemberRole;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static hk.ljx.fishpicsbackend.common.constants.SpaceConstants.SPACE_TYPE_TEAM;

@Component
public class SpacePermissionChecker {

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    @Resource
    private RedisCacheManager cacheManager;

    public boolean isTeamSpace(Space space) {
        return space != null && ExcUtils.eq(space.getType(), SPACE_TYPE_TEAM);
    }

    public boolean isTeamOwner(Long spaceId, Long userId) {
        if (spaceId == null || userId == null) {
            return false;
        }
        String key = spaceId + ":" + userId + ":owner";
        Boolean cached = cacheManager.getTeamMemberCache().get(key, Boolean.class);
        if (cached != null) {
            return cached;
        }
        SpaceTeamMember member = spaceTeamMemberMapper.selectOne(
                new LambdaQueryWrapper<SpaceTeamMember>()
                        .eq(SpaceTeamMember::getSpaceId, spaceId)
                        .eq(SpaceTeamMember::getUserId, userId)
                        .eq(SpaceTeamMember::getRoleId, TeamMemberRole.OWNER.code()));
        boolean isOwner = member != null;
        cacheManager.getTeamMemberCache().put(key, isOwner);
        return isOwner;
    }

    public boolean canAccess(Space space, Long userId) {
        if (space == null || userId == null) {
            return false;
        }
        if (Objects.equals(space.getUserId(), userId)) {
            return true;
        }
        return isTeamSpace(space) && isMember(space.getId(), userId);
    }

    public boolean canManageMembers(Space space, User operator) {
        if (space == null || operator == null || operator.getId() == null) {
            return false;
        }
        return Objects.equals(space.getUserId(), operator.getId())
                || isTeamOwner(space.getId(), operator.getId());
    }

    public boolean canUpdateSpace(Space space, User operator) {
        if (canManageMembers(space, operator)) {
            return true;
        }
        return operator != null && Role.isAdmin(operator.getRole());
    }

    public void checkAccess(Space space, Long userId) {
        ExcUtils.throwIfTrue(!canAccess(space, userId), ExceptionCode.FORBIDDEN, "无权访问该空间");
    }

    public void checkManageMembers(Space space, User operator) {
        ExcUtils.throwIfTrue(!canManageMembers(space, operator),
                ExceptionCode.FORBIDDEN, "仅空间创建者或团队所有者可管理成员");
    }

    public void checkUpdateSpace(Space space, User operator) {
        ExcUtils.throwIfTrue(!canUpdateSpace(space, operator),
                ExceptionCode.FORBIDDEN, "无权限修改空间信息");
    }

    private boolean isMember(Long spaceId, Long userId) {
        String key = spaceId + ":" + userId + ":member";
        Boolean cached = cacheManager.getTeamMemberCache().get(key, Boolean.class);
        if (cached != null) {
            return cached;
        }
        Long memberCount = spaceTeamMemberMapper.selectCount(
                new LambdaQueryWrapper<SpaceTeamMember>()
                        .eq(SpaceTeamMember::getSpaceId, spaceId)
                        .eq(SpaceTeamMember::getUserId, userId));
        boolean isMember = memberCount != null && memberCount > 0;
        cacheManager.getTeamMemberCache().put(key, isMember);
        return isMember;
    }
}
