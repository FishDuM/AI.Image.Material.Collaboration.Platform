package hk.ljx.fishpicsbackend.user.service.impl;

import hk.ljx.fishpicsbackend.user.vo.UserVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserServiceImplTest {

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
