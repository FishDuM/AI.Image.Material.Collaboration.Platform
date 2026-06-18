package hk.ljx.fishpicsbackend.picture.component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.space.enums.TeamMemberRole;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SpaceWritePermissionChecker {

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    public void check(Space space, Long userId) {
        Space.validateActive(space);
        if (Objects.equals(space.getUserId(), userId)) {
            return;
        }

        LoginContext context = UserHolder.getLoginContext();
        if (context != null && context.isAdmin()) {
            return;
        }

        ExcUtils.throwIfTrue(context == null || context.getUserId() == null, ExceptionCode.NOT_LOGIN);
        if (ExcUtils.eq(space.getType(), 0)) {
            throw new BaseException(ExceptionCode.FORBIDDEN, "无权访问该私人空间");
        }

        Long memberCount = spaceTeamMemberMapper.selectCount(
                new LambdaQueryWrapper<SpaceTeamMember>()
                        .eq(SpaceTeamMember::getSpaceId, space.getId())
                        .eq(SpaceTeamMember::getUserId, userId)
                        .in(SpaceTeamMember::getRoleId, TeamMemberRole.WRITABLE_ROLE_IDS));
        ExcUtils.throwIfTrue(memberCount == null || memberCount <= 0,
                ExceptionCode.FORBIDDEN, "无权写入该团队空间");
    }
}
