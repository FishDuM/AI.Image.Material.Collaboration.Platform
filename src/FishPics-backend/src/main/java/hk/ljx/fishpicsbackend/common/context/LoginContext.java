package hk.ljx.fishpicsbackend.common.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 登录上下文（用户 + 权限）
 * 存储在 ThreadLocal 和 Redis 中
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginContext {

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    /** 用户状态：1=正常，0=禁用 */
    private Integer status;

    /**
     * 用户等级：0=普通，1=VIP，2=SVIP
     */
    private Integer level;

    /**
     * 用户角色：0=普通，1=管理员
     */
    private Integer role;

    /**
     * 是否是管理员（Role.ADMIN）
     */
    private Boolean isAdmin;

    /**
     * VIP 专属权限（由 level 自动生成，不手动分配）
     */
    private List<String> vipPerms;

    private List<String> systemPerms;

    /**
     * 团队权限映射：key=spaceId(String), value=团队权限信息
     * 注意：使用 String key 而非 Long，确保 Redis JSON 序列化/反序列化安全
     */
    private Map<String, TeamPerm> teams;

    /**
     * 团队权限信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamPerm {

        private Integer roleId;

        private String roleName;

        private List<String> perms;
    }

    public boolean isAdmin() {
        return Boolean.TRUE.equals(isAdmin);
    }

    public boolean hasSystemPerm(String perm) {
        return isAdmin() || (systemPerms != null && systemPerms.contains(perm));
    }

    public boolean inTeam(Long spaceId) {
        if (spaceId == null) return false;
        return isAdmin() || (teams != null && teams.containsKey(String.valueOf(spaceId)));
    }

    public boolean hasTeamPerm(Long spaceId, String perm) {
        if (isAdmin()) {
            return true;
        }
        if (spaceId == null || teams == null) {
            return false;
        }
        TeamPerm teamPerm = teams.get(String.valueOf(spaceId));
        if (teamPerm == null) {
            return false;
        }
        return teamPerm.getPerms() != null && teamPerm.getPerms().contains(perm);
    }

    public Integer getTeamRoleId(Long spaceId) {
        if (isAdmin()) {
            return 1;
        }
        if (spaceId == null || teams == null) {
            return null;
        }
        TeamPerm teamPerm = teams.get(String.valueOf(spaceId));
        return teamPerm != null ? teamPerm.getRoleId() : null;
    }

    public boolean hasVipPerm(String perm) {
        return vipPerms != null && vipPerms.contains(perm);
    }
}
