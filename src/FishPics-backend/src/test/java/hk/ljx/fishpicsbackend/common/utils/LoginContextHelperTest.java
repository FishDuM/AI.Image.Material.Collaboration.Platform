package hk.ljx.fishpicsbackend.common.utils;

import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoginContextHelper 单元测试
 */
class LoginContextHelperTest {

    @AfterEach
    void cleanup() {
        UserHolder.removeLoginContext();
    }

    // ==================== requireLoginContext ====================

    @Test
    @DisplayName("requireLoginContext - 未设置上下文应抛 NOT_LOGIN")
    void requireLoginContext_noContext_throws() {
        assertThrows(BaseException.class, LoginContextHelper::requireLoginContext);
    }

    @Test
    @DisplayName("requireLoginContext - userId 为 null 应抛 NOT_LOGIN")
    void requireLoginContext_nullUserId_throws() {
        UserHolder.setLoginContext(LoginContext.builder().userId(null).build());
        assertThrows(BaseException.class, LoginContextHelper::requireLoginContext);
    }

    @Test
    @DisplayName("requireLoginContext - 正常上下文应返回")
    void requireLoginContext_valid_returns() {
        LoginContext ctx = LoginContext.builder().userId(1L).username("test").build();
        UserHolder.setLoginContext(ctx);

        LoginContext result = LoginContextHelper.requireLoginContext();
        assertNotNull(result);
        assertEquals(1L, result.getUserId());
    }

    // ==================== requireUser ====================

    @Test
    @DisplayName("requireUser - 未设置上下文应抛 NOT_LOGIN")
    void requireUser_noContext_throws() {
        assertThrows(BaseException.class, LoginContextHelper::requireUser);
    }

    @Test
    @DisplayName("requireUser - userId 为 null 应抛 NOT_LOGIN")
    void requireUser_nullUserId_throws() {
        UserHolder.setLoginContext(LoginContext.builder().userId(null).build());
        assertThrows(BaseException.class, LoginContextHelper::requireUser);
    }

    @Test
    @DisplayName("requireUser - 正常上下文应返回 User 对象")
    void requireUser_valid_returnsUser() {
        UserHolder.setLoginContext(LoginContext.builder()
                .userId(42L)
                .username("alice")
                .nickname("Alice")
                .avatar("http://example.com/a.png")
                .status(1)
                .level(2)
                .role(0)
                .build());

        User user = LoginContextHelper.requireUser();
        assertNotNull(user);
        assertEquals(42L, user.getId());
        assertEquals("alice", user.getUsername());
        assertEquals("Alice", user.getNickname());
        assertEquals(2, user.getLevel());
    }
}
