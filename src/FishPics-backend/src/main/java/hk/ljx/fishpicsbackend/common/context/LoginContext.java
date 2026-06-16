package hk.ljx.fishpicsbackend.common.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
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
public class LoginContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 用户状态：1=正常，0=禁用
     */
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
     * 是否是管理员（role == 1）
     */
    private Boolean isAdmin;

    /**
     * VIP 专属权限（由 level 自动生成，不手动分配）
     */
    private List<String> vipPerms;

    /**
     * 系统角色ID（null 表示不是系统角色）
     */
    /**
     * 系统权限列表
     */
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
    public static class TeamPerm implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 团队角色ID
         */
        private Integer roleId;

        /**
         * 团队角色名称
         */
        private String roleName;

        /**
         * 团队权限列表
         */
        private List<String> perms;
    }

    /**
     * 判断是否为管理员（role == 1）
     */
    public boolean isAdmin() {
        return Boolean.TRUE.equals(isAdmin);
    }

    /**
     * 判断是否为系统超管（保留兼容性）
     */
    /**
     * 判断是否拥有指定系统权限
     */
    public boolean hasSystemPerm(String perm) {
        return isAdmin() || (systemPerms != null && systemPerms.contains(perm));
    }

    /**
     * 判断是否在指定团队中
     */
    public boolean inTeam(Long spaceId) {
        if (spaceId == null) return false;
        return isAdmin() || (teams != null && teams.containsKey(String.valueOf(spaceId)));
    }

    /**
     * 判断是否拥有指定团队权限
     */
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

    /**
     * 获取指定团队的角色ID
     */
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

    /**
     * 判断是否拥有指定 VIP 权限
     */
    public boolean hasVipPerm(String perm) {
        return vipPerms != null && vipPerms.contains(perm);
    }
}
