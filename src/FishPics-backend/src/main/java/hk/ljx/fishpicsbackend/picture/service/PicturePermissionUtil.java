package hk.ljx.fishpicsbackend.picture.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.user.entity.User;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class PicturePermissionUtil {

    private PicturePermissionUtil() {}

    public enum Op { READ, EDIT_META, REPLACE_FILE, DELETE }

    public static void checkWrite(Picture picture, Op op,
                                  BaseMapper<SpaceTeamMember> teamMemberMapper) {
        User current = UserHolder.getUser();
        if (current == null) {
            throw new BaseException(ExceptionCode.NOT_LOGIN);
        }

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
            if (member != null && Integer.valueOf(1).equals(member.getRoleId())) {
                return;
            }
        }

        throw new BaseException(ExceptionCode.FORBIDDEN, "没有权限" + opDesc(op) + "这张图片");
    }

    public static List<Long> filterDeletableIds(List<Picture> pictures, List<Long> requestedIds,
                                               BaseMapper<SpaceTeamMember> teamMemberMapper) {
        User current = UserHolder.getUser();
        if (current == null) {
            throw new BaseException(ExceptionCode.NOT_LOGIN);
        }

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
            Set<Long> ownedSpaceIds = teamMemberMapper.selectList(
                            new LambdaQueryWrapper<SpaceTeamMember>()
                                    .in(SpaceTeamMember::getSpaceId, spaceIds)
                                    .eq(SpaceTeamMember::getUserId, current.getId())
                                    .eq(SpaceTeamMember::getRoleId, 1))
                    .stream()
                    .map(SpaceTeamMember::getSpaceId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            pictures.stream()
                    .filter(p -> p.getId() != null
                            && p.getSpaceId() != null
                            && ownedSpaceIds.contains(p.getSpaceId()))
                    .map(Picture::getId)
                    .forEach(allowedIds::add);
        }

        return requestedIds.stream()
                .filter(allowedIds::contains)
                .collect(Collectors.toList());
    }

    private static String opDesc(Op op) {
        return switch (op) {
            case READ -> "查看";
            case EDIT_META -> "编辑";
            case REPLACE_FILE -> "替换";
            case DELETE -> "删除";
        };
    }
}
