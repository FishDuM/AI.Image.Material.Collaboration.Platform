package hk.ljx.fishpicsbackend.common.utils;

import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.enums.Role;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.space.enums.TeamMemberRole;
import hk.ljx.fishpicsbackend.user.entity.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class PermissionUtils {

    private PermissionUtils() {}

    public static LoginContext buildLoginContext(User user, List<SpaceTeamMember> teamMemberships) {
        boolean isAdmin = Role.isAdmin(user.getRole());
        List<String> permissions = getPermissionsByLevel(user.getLevel(), user.getRole());
        List<String> systemPerms = permissions.stream()
                .filter(permission -> permission.startsWith("system:"))
                .collect(Collectors.toList());
        List<String> vipPerms = permissions.stream()
                .filter(permission -> !permission.startsWith("system:"))
                .collect(Collectors.toList());

        Map<String, LoginContext.TeamPerm> teams = null;
        if (teamMemberships != null && !teamMemberships.isEmpty()) {
            teams = new HashMap<>();
            for (SpaceTeamMember m : teamMemberships) {
                if (m.getSpaceId() == null) continue;
                LoginContext.TeamPerm tp = LoginContext.TeamPerm.builder()
                        .roleId(m.getRoleId())
                        .roleName(null)
                        .perms(getPermsByTeamRoleId(m.getRoleId()))
                        .build();
                teams.put(String.valueOf(m.getSpaceId()), tp);
            }
        }

        return LoginContext.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .level(user.getLevel())
                .role(user.getRole())
                .isAdmin(isAdmin)
                .systemPerms(systemPerms)
                .vipPerms(vipPerms)
                .teams(teams)
                .build();
    }

    private static List<String> getPermsByTeamRoleId(Integer roleId) {
        if (roleId == null) return List.of();
        return switch (roleId) {
            case 1 -> TeamMemberRole.isOwner(roleId) ? List.of("team:view", "team:edit", "team:delete", "team:invite", "team:kick", "team:transfer") : List.of();
            case 2 -> roleId == TeamMemberRole.MEMBER.code() ? List.of("team:view", "team:edit", "team:invite", "team:kick") : List.of();
            case 3 -> roleId == TeamMemberRole.EDITOR.code() ? List.of("team:view", "team:edit") : List.of();
            case 4 -> roleId == TeamMemberRole.VIEWER.code() ? List.of("team:view") : List.of();
            default -> List.of();
        };
    }

    public static List<String> getPermissionsByLevel(Integer level, Integer role) {
        List<String> permissions = new ArrayList<>();
        // 系统权限由 role 决定
        if (Role.isAdmin(role)) {
            permissions.add("system:user:manage");
            permissions.add("system:team:manage");
            permissions.add("system:ai:manage");
            permissions.add("system:log:manage");
            permissions.add("system:config");
            permissions.add("space:create");
            permissions.add("space:manage");
            permissions.add("picture:upload");
            permissions.add("picture:manage");
            return permissions;
        }
        // 业务等级权限由 level 决定
        if (level == null) level = 0;
        permissions.add("space:create");
        permissions.add("picture:upload");
        if (level >= 2) {
            permissions.add("picture:manage");
            permissions.add("ai:advanced");
        } else if (level >= 1) {
            permissions.add("picture:manage");
        }
        return permissions;
    }
}
