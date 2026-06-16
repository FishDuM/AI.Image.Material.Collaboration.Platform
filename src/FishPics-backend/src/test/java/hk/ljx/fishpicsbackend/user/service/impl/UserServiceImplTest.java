package hk.ljx.fishpicsbackend.user.service.impl;

import cn.hutool.core.util.ReflectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.user.dto.UserQueryWrapper;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.vo.UserVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceImplTest {

    private final UserServiceImpl userService = new UserServiceImpl();

    @SuppressWarnings("unchecked")
    private QueryWrapper<User> invokeNewQueryWrapper(UserQueryWrapper request) {
        Method method = ReflectUtil.getMethod(UserServiceImpl.class, "newQueryWrapper", UserQueryWrapper.class);
        assertNotNull(method);
        return (QueryWrapper<User>) ReflectUtil.invoke(userService, method, request);
    }

    @Test
    @DisplayName("newQueryWrapper 应允许白名单排序字段")
    void newQueryWrapperShouldAllowWhitelistedSortField() {
        UserQueryWrapper request = new UserQueryWrapper();
        request.setSortField("create_time");
        request.setSortOrder("ascend");

        QueryWrapper<User> result = invokeNewQueryWrapper(request);

        String sql = result.getSqlSegment();
        assertNotNull(sql);
        assertTrue(sql.contains("ORDER BY"));
        assertTrue(sql.contains("create_time"));
    }

    @Test
    @DisplayName("newQueryWrapper 应拦截非法排序字段")
    void newQueryWrapperShouldRejectIllegalSortField() {
        UserQueryWrapper request = new UserQueryWrapper();
        request.setSortField("1; DROP TABLE user; --");
        request.setSortOrder("ascend");

        QueryWrapper<User> result = invokeNewQueryWrapper(request);

        String sql = result.getSqlSegment();
        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains(";"));
        assertFalse(sql.contains("ORDER BY"));
    }

    @Test
    @DisplayName("newQueryWrapper 空排序字段时不应追加排序")
    void newQueryWrapperShouldIgnoreBlankSortField() {
        UserQueryWrapper request = new UserQueryWrapper();
        request.setSortField("");
        request.setSortOrder("ascend");

        QueryWrapper<User> result = invokeNewQueryWrapper(request);

        String sql = result.getSqlSegment();
        if (sql != null) {
            assertFalse(sql.contains("ORDER BY"));
        }
    }

    @Test
    @DisplayName("管理员用户 VO 不应暴露密码访问器")
    void adminUserVoShouldNotExposePasswordAccessor() {
        UserVO vo = UserVO.ofAdmin(
                1L,
                "testuser",
                "测试用户",
                "https://example.com/avatar.jpg",
                "test@example.com",
                "13800138000",
                1,
                0,
                null,
                null
        );

        assertNotNull(vo);
        assertThrows(NoSuchMethodException.class, () -> UserVO.class.getMethod("getPassword"));
        assertThrows(NoSuchMethodException.class, () -> UserVO.class.getMethod("setPassword", String.class));
    }

    @Test
    @DisplayName("公开资料 VO 应只包含公开字段")
    void publicProfileVoShouldOnlyContainPublicFields() {
        UserVO vo = UserVO.ofPublicProfile(1L, "testuser", "测试用户", "avatar", 1, null);

        assertNotNull(vo);
        assertNull(vo.getEmail());
        assertNull(vo.getPhone());
        assertNull(vo.getPermissions());
        assertDoesNotThrow(vo::getNickname);
    }
}
