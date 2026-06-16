package hk.ljx.fishpicsbackend.common.utils;

import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import hk.ljx.fishpicsbackend.user.entity.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 权限工具类：根据用户等级构建权限列表和 LoginContext
 */
public final class PermissionUtils {

    private PermissionUtils() {}

    /**
     * 之前 buildLoginContext 不填 teams，虽然目前业务未直接调 inTeam/hasTeamPerm
     * (实际都直查 SpaceTeamMemberMapper)，但 LoginContext.teams 字段已定义，
     * 未来接入团队权限判断时会有问题。这里注入 teams。
     */
    public static LoginContext buildLoginContext(User user, List<SpaceTeamMember> teamMemberships) {
        boolean isAdmin = user.getRole() != null && user.getRole() == 1;
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

    /**
     * 旧签名(无 teams 注入)— 保留以兼容外部调用,内部用空列表
     */
    /**
     * 根据团队角色 ID 给出该角色拥有的权限码列表
     * roleId: 1=所有者(全部), 2=管理员(管理), 3=编辑(编辑+查看), 4=查看者(仅查看)
     */
    private static List<String> getPermsByTeamRoleId(Integer roleId) {
        if (roleId == null) return List.of();
        return switch (roleId) {
            case 1 -> List.of("team:view", "team:edit", "team:delete", "team:invite", "team:kick", "team:transfer");
            case 2 -> List.of("team:view", "team:edit", "team:invite", "team:kick");
            case 3 -> List.of("team:view", "team:edit");
            case 4 -> List.of("team:view");
            default -> List.of();
        };
    }

    public static List<String> getPermissionsByLevel(Integer level, Integer role) {
        List<String> permissions = new ArrayList<>();
        // 系统权限由 role 决定
        if (role != null && role == 1) {
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

    /**
     * 兼容旧签名（仅 level）
     */
}
