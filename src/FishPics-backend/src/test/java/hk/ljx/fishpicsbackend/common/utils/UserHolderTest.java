package hk.ljx.fishpicsbackend.common.utils;

import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserHolder 单元测试
 */
class UserHolderTest {

    @AfterEach
    void cleanup() {
        UserHolder.removeLoginContext();
    }

    @Test
    @DisplayName("初始状态 - getLoginContext 应返回 null")
    void getLoginContext_initial_returnsNull() {
        assertNull(UserHolder.getLoginContext());
    }

    @Test
    @DisplayName("初始状态 - getUser 应返回 null")
    void getUser_initial_returnsNull() {
        assertNull(UserHolder.getUser());
    }

    @Test
    @DisplayName("setLoginContext - 设置后应能正确获取")
    void setLoginContext_thenGet_returnsContext() {
        LoginContext ctx = LoginContext.builder()
                .userId(100L)
                .username("testuser")
                .nickname("测试用户")
                .avatar("http://example.com/avatar.png")
                .status(1)
                .level(1)
                .role(0)
                .systemPerms(List.of("system:user:manage"))
                .build();

        UserHolder.setLoginContext(ctx);

        LoginContext result = UserHolder.getLoginContext();
        assertNotNull(result);
        assertEquals(100L, result.getUserId());
        assertEquals("testuser", result.getUsername());
    }

    @Test
    @DisplayName("getUser - 应从 LoginContext 构建 User 对象")
    void getUser_withContext_buildsUser() {
        LoginContext ctx = LoginContext.builder()
                .userId(42L)
                .username("alice")
                .nickname("Alice")
                .avatar("http://example.com/a.png")
                .status(1)
                .level(2)
                .role(1)
                .build();
        UserHolder.setLoginContext(ctx);

        User user = UserHolder.getUser();
        assertNotNull(user);
        assertEquals(42L, user.getId());
        assertEquals("alice", user.getUsername());
        assertEquals("Alice", user.getNickname());
        assertEquals("http://example.com/a.png", user.getAvatar());
        assertEquals(1, user.getStatus());
        assertEquals(2, user.getLevel());
        assertEquals(1, user.getRole());
    }

    @Test
    @DisplayName("removeLoginContext - 清除后应返回 null")
    void removeLoginContext_thenGet_returnsNull() {
        UserHolder.setLoginContext(LoginContext.builder().userId(1L).build());
        assertNotNull(UserHolder.getLoginContext());

        UserHolder.removeLoginContext();
        assertNull(UserHolder.getLoginContext());
        assertNull(UserHolder.getUser());
    }

    @Test
    @DisplayName("线程隔离 - 不同线程的上下文应互不影响")
    void threadIsolation() throws Exception {
        LoginContext ctxA = LoginContext.builder().userId(1L).username("threadA").build();
        UserHolder.setLoginContext(ctxA);

        // 另一个线程应看不到 threadA 的上下文
        Thread otherThread = new Thread(() -> {
            assertNull(UserHolder.getLoginContext(), "其他线程不应看到 threadA 的上下文");

            LoginContext ctxB = LoginContext.builder().userId(2L).username("threadB").build();
            UserHolder.setLoginContext(ctxB);
            assertNotNull(UserHolder.getLoginContext());
            assertEquals(2L, UserHolder.getLoginContext().getUserId());

            UserHolder.removeLoginContext();
        });
        otherThread.start();
        otherThread.join(3000);

        // threadA 的上下文应仍在
        assertEquals(1L, UserHolder.getLoginContext().getUserId());
    }
}
