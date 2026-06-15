package hk.ljx.fishpicsbackend.picture.service;

import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.user.entity.User;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;
import java.util.Objects;

/**
 * picture 写权限统一工具
 */
public final class PicturePermissionUtil {

    private PicturePermissionUtil() {}

    /** 操作类型 */
    public enum Op { READ, EDIT_META, REPLACE_FILE, DELETE }

    /**
     * 校验用户对单张 picture 的写权限
     * @param op EDIT_META / REPLACE_FILE / DELETE
     */
    public static void checkWrite(Picture picture, Op op,
                                  BaseMapper<SpaceTeamMember> teamMemberMapper) {
        User current = UserHolder.getUser();
        if (current == null) {
            throw new BaseException(ExceptionCode.NOT_LOGIN);
        }
        LoginContext ctx = UserHolder.getLoginContext();
        boolean isAdmin = ctx != null && ctx.hasSystemPerm("system:user:manage");

        if (isAdmin) return; // admin 全部放行

        if (picture.getUserId() != null && picture.getUserId().equals(current.getId())) {
            return; // owner
        }

        // team 成员权限:仅 roleId=1 所有者(roleId=1)能改/删他人图;roleId=2 成员只能操作自己上传的
        if (picture.getSpaceId() != null) {
            SpaceTeamMember member = teamMemberMapper.selectOne(
                    new LambdaQueryWrapper<SpaceTeamMember>()
                            .eq(SpaceTeamMember::getSpaceId, picture.getSpaceId())
                            .eq(SpaceTeamMember::getUserId, current.getId()));
            if (member != null && Integer.valueOf(1).equals(member.getRoleId())) {
                return; // space owner 团队角色
            }
        }

        // 已认证但权限不足 → FORBIDDEN
        throw new BaseException(ExceptionCode.FORBIDDEN, "没有权限" + opDesc(op) + "这张图片");
    }

    /**
     * 批量删除权限 — team 成员只能删自己上传的图
     * @return 真正可以删的 id 列表
     */
    public static List<Long> filterDeletableIds(List<Picture> pictures, List<Long> requestedIds,
                                               BaseMapper<SpaceTeamMember> teamMemberMapper) {
        User current = UserHolder.getUser();
        if (current == null) {
            throw new BaseException(ExceptionCode.NOT_LOGIN);
        }
        LoginContext ctx = UserHolder.getLoginContext();
        boolean isAdmin = ctx != null && ctx.hasSystemPerm("system:user:manage");
        if (isAdmin) {
            return requestedIds; // admin 全放
        }

        // 收集用户有权限删的 pictureId
        java.util.Set<Long> ownerIds = new java.util.HashSet<>();
        java.util.Set<Long> teamOwnedSpaceIds = new java.util.HashSet<>();
        for (Picture p : pictures) {
            if (p.getUserId() != null && p.getUserId().equals(current.getId())) {
                ownerIds.add(p.getId());
            }
            // team owner 角色
            if (p.getSpaceId() != null) {
                SpaceTeamMember member = teamMemberMapper.selectOne(
                        new LambdaQueryWrapper<SpaceTeamMember>()
                                .eq(SpaceTeamMember::getSpaceId, p.getSpaceId())
                                .eq(SpaceTeamMember::getUserId, current.getId()));
                if (member != null && Integer.valueOf(1).equals(member.getRoleId())) {
                    teamOwnedSpaceIds.add(p.getId());
                }
            }
        }
        java.util.Set<Long> allowed = new java.util.HashSet<>();
        allowed.addAll(ownerIds);
        allowed.addAll(teamOwnedSpaceIds);
        // 只保留 allowed 内的 id
        return requestedIds.stream().filter(allowed::contains).collect(java.util.stream.Collectors.toList());
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
