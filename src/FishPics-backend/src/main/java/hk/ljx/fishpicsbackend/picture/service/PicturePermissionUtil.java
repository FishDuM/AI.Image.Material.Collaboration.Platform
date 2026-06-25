package hk.ljx.fishpicsbackend.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.space.enums.TeamMemberRole;
import hk.ljx.fishpicsbackend.user.entity.User;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class PicturePermissionUtil {

    private PicturePermissionUtil() {}

    public static void checkWrite(Picture picture, String opDesc,
                                  BaseMapper<SpaceTeamMember> teamMemberMapper) {
        checkWrite(picture, opDesc, false, teamMemberMapper);
    }

    public static void checkWrite(Picture picture, String opDesc, boolean ownerOnly,
                                  BaseMapper<SpaceTeamMember> teamMemberMapper) {
        User current = LoginContextHelper.requireUser();

        LoginContext ctx = UserHolder.getLoginContext();
        boolean isAdmin = ctx != null && ctx.hasSystemPerm("system:user:manage");
        if (isAdmin) {
            return;
        }

        if (Objects.equals(picture.getUserId(), current.getId())) {
            return;
        }

        if (picture.getSpaceId() != null) {
            SpaceTeamMember member = teamMemberMapper.selectOne(
                    new LambdaQueryWrapper<SpaceTeamMember>()
                            .eq(SpaceTeamMember::getSpaceId, picture.getSpaceId())
                            .eq(SpaceTeamMember::getUserId, current.getId()));
            if (canTeamMemberWrite(member, ownerOnly)) {
                return;
            }
        }

        throw new BaseException(ExceptionCode.FORBIDDEN, "没有权限" + opDesc + "这张图片");
    }

    private static boolean canTeamMemberWrite(SpaceTeamMember member, boolean ownerOnly) {
        if (member == null || member.getRoleId() == null) {
            return false;
        }
        if (ownerOnly) {
            return TeamMemberRole.isOwner(member.getRoleId());
        }
        return TeamMemberRole.isWritable(member.getRoleId());
    }

    public static List<Long> filterDeletableIds(List<Picture> pictures, List<Long> requestedIds,
                                               BaseMapper<SpaceTeamMember> teamMemberMapper) {
        User current = LoginContextHelper.requireUser();

        LoginContext ctx = UserHolder.getLoginContext();
        boolean isAdmin = ctx != null && ctx.hasSystemPerm("system:user:manage");
        if (isAdmin) {
            return requestedIds;
        }

        Set<Long> allowedIds = pictures.stream()
                .filter(p -> Objects.equals(p.getUserId(), current.getId()))
                .map(Picture::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Set<Long> spaceIds = pictures.stream()
                .map(Picture::getSpaceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!spaceIds.isEmpty()) {
            Set<Long> writableSpaceIds = teamMemberMapper.selectList(
                            new LambdaQueryWrapper<SpaceTeamMember>()
                                    .in(SpaceTeamMember::getSpaceId, spaceIds)
                                    .eq(SpaceTeamMember::getUserId, current.getId()))
                    .stream()
                    .filter(m -> TeamMemberRole.isWritable(m.getRoleId()))
                    .map(SpaceTeamMember::getSpaceId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            pictures.stream()
                    .filter(p -> p.getId() != null
                            && p.getSpaceId() != null
                            && writableSpaceIds.contains(p.getSpaceId()))
                    .map(Picture::getId)
                    .forEach(allowedIds::add);
        }

        return requestedIds.stream()
                .filter(allowedIds::contains)
                .collect(Collectors.toList());
    }
}
