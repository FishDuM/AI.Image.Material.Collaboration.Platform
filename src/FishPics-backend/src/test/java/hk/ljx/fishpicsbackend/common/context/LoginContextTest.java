package hk.ljx.fishpicsbackend.common.context;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoginContext 单元测试
 */
class LoginContextTest {

    // ==================== isAdmin / hasSystemPerm ====================

    @Test
    @DisplayName("isAdmin - isAdmin=true 时应返回 true")
    void isAdmin_true_returnsTrue() {
        LoginContext ctx = LoginContext.builder().isAdmin(true).build();
        assertTrue(ctx.isAdmin());
    }

    @Test
    @DisplayName("isAdmin - isAdmin=false 时应返回 false")
    void isAdmin_false_returnsFalse() {
        LoginContext ctx = LoginContext.builder().isAdmin(false).build();
        assertFalse(ctx.isAdmin());
    }

    @Test
    @DisplayName("isAdmin - isAdmin=null 时应返回 false")
    void isAdmin_null_returnsFalse() {
        LoginContext ctx = LoginContext.builder().build();
        assertFalse(ctx.isAdmin());
    }

    @Test
    @DisplayName("hasSystemPerm - 管理员拥有所有系统权限")
    void hasSystemPerm_admin_hasAll() {
        LoginContext ctx = LoginContext.builder().isAdmin(true).build();
        assertTrue(ctx.hasSystemPerm("system:user:manage"));
        assertTrue(ctx.hasSystemPerm("system:any:perm"));
    }

    @Test
    @DisplayName("hasSystemPerm - 普通用户有对应权限时返回 true")
    void hasSystemPerm_normalUser_hasPerm() {
        LoginContext ctx = LoginContext.builder()
                .isAdmin(false)
                .systemPerms(List.of("system:user:manage", "system:log:manage"))
                .build();
        assertTrue(ctx.hasSystemPerm("system:user:manage"));
        assertFalse(ctx.hasSystemPerm("system:config"));
    }

    @Test
    @DisplayName("hasSystemPerm - 普通用户无权限时返回 false")
    void hasSystemPerm_normalUser_noPerm() {
        LoginContext ctx = LoginContext.builder()
                .isAdmin(false)
                .systemPerms(List.of("system:log:manage"))
                .build();
        assertFalse(ctx.hasSystemPerm("system:user:manage"));
    }

    @Test
    @DisplayName("hasSystemPerm - systemPerms 为 null 时返回 false")
    void hasSystemPerm_nullPerms_returnsFalse() {
        LoginContext ctx = LoginContext.builder().isAdmin(false).build();
        assertFalse(ctx.hasSystemPerm("system:user:manage"));
    }

    // ==================== inTeam / hasTeamPerm ====================

    @Test
    @DisplayName("inTeam - 管理员在所有团队中")
    void inTeam_admin_returnsTrue() {
        LoginContext ctx = LoginContext.builder().isAdmin(true).build();
        assertTrue(ctx.inTeam(100L));
    }

    @Test
    @DisplayName("inTeam - 普通用户在指定团队中")
    void inTeam_normalUser_inTeam() {
        LoginContext ctx = LoginContext.builder()
                .isAdmin(false)
                .teams(Map.of("100", LoginContext.TeamPerm.builder().roleId(1).build()))
                .build();
        assertTrue(ctx.inTeam(100L));
        assertFalse(ctx.inTeam(200L));
    }

    @Test
    @DisplayName("inTeam - spaceId 为 null 时返回 false")
    void inTeam_nullSpaceId_returnsFalse() {
        LoginContext ctx = LoginContext.builder().isAdmin(false).build();
        assertFalse(ctx.inTeam(null));
    }

    @Test
    @DisplayName("hasTeamPerm - 管理员拥有所有团队权限")
    void hasTeamPerm_admin_hasAll() {
        LoginContext ctx = LoginContext.builder().isAdmin(true).build();
        assertTrue(ctx.hasTeamPerm(100L, "team:edit"));
    }

    @Test
    @DisplayName("hasTeamPerm - 普通用户有对应团队权限时返回 true")
    void hasTeamPerm_normalUser_hasPerm() {
        LoginContext ctx = LoginContext.builder()
                .isAdmin(false)
                .teams(Map.of("100", LoginContext.TeamPerm.builder()
                        .roleId(2)
                        .perms(List.of("team:view", "team:edit"))
                        .build()))
                .build();
        assertTrue(ctx.hasTeamPerm(100L, "team:edit"));
        assertFalse(ctx.hasTeamPerm(100L, "team:delete"));
        assertFalse(ctx.hasTeamPerm(200L, "team:edit"));
    }

    @Test
    @DisplayName("hasTeamPerm - teams 为 null 时返回 false")
    void hasTeamPerm_nullTeams_returnsFalse() {
        LoginContext ctx = LoginContext.builder().isAdmin(false).build();
        assertFalse(ctx.hasTeamPerm(100L, "team:edit"));
    }

    // ==================== getTeamRoleId ====================

    @Test
    @DisplayName("getTeamRoleId - 管理员始终返回 1")
    void getTeamRoleId_admin_returns1() {
        LoginContext ctx = LoginContext.builder().isAdmin(true).build();
        assertEquals(1, ctx.getTeamRoleId(100L));
    }

    @Test
    @DisplayName("getTeamRoleId - 普通用户返回对应团队角色 ID")
    void getTeamRoleId_normalUser_returnsRoleId() {
        LoginContext ctx = LoginContext.builder()
                .isAdmin(false)
                .teams(Map.of("100", LoginContext.TeamPerm.builder().roleId(3).build()))
                .build();
        assertEquals(3, ctx.getTeamRoleId(100L));
        assertNull(ctx.getTeamRoleId(200L));
    }

    // ==================== hasVipPerm ====================

    @Test
    @DisplayName("hasVipPerm - 有 VIP 权限时返回 true")
    void hasVipPerm_hasPerm_returnsTrue() {
        LoginContext ctx = LoginContext.builder()
                .vipPerms(List.of("vip:upload", "vip:ai"))
                .build();
        assertTrue(ctx.hasVipPerm("vip:upload"));
        assertFalse(ctx.hasVipPerm("vip:admin"));
    }

    @Test
    @DisplayName("hasVipPerm - vipPerms 为 null 时返回 false")
    void hasVipPerm_null_returnsFalse() {
        LoginContext ctx = LoginContext.builder().build();
        assertFalse(ctx.hasVipPerm("vip:upload"));
    }
}
