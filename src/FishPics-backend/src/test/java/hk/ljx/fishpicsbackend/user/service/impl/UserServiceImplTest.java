package hk.ljx.fishpicsbackend.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.mapper.UserMapper;
import hk.ljx.fishpicsbackend.user.dto.UserQueryWrapper;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.vo.AdminGetUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * UserServiceImpl 单元测试
 * 测试 SQL 注入防护等功能
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserMapper userMapper;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setPassword("hashedpassword");
        testUser.setNickname("测试用户");
        testUser.setAvatar("https://example.com/avatar.jpg");
        testUser.setEmail("test@example.com");
        testUser.setPhone("13800138000");
        testUser.setStatus(1);
        testUser.setRole("user");
        testUser.setLevel(0);
    }

    @Test
    @DisplayName("测试 sortField 白名单校验 - 允许的字段")
    void testNewQueryWrapper_AllowedSortFields() {
        // Given
        UserQueryWrapper queryWrapper = new UserQueryWrapper();
        queryWrapper.setSortField("create_time");
        queryWrapper.setSortOrder("ascend");

        // When
        QueryWrapper<User> result = userService.newQueryWrapper(queryWrapper);

        // Then
        assertNotNull(result);
        // 验证排序条件被正确添加
        String sql = result.getSqlSegment();
        assertTrue(sql.contains("ORDER BY"));
        assertTrue(sql.contains("create_time"));
    }

    @Test
    @DisplayName("测试 sortField 白名单校验 - 拒绝非法字段")
    void testNewQueryWrapper_RejectInvalidSortField() {
        // Given
        UserQueryWrapper queryWrapper = new UserQueryWrapper();
        queryWrapper.setSortField("1; DROP TABLE user; --");
        queryWrapper.setSortOrder("ascend");

        // When
        QueryWrapper<User> result = userService.newQueryWrapper(queryWrapper);

        // Then
        assertNotNull(result);
        String sql = result.getSqlSegment();
        // 验证恶意 SQL 不会被添加到排序中
        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains(";"));
    }

    @Test
    @DisplayName("测试 sortField 白名单校验 - null 值")
    void testNewQueryWrapper_NullSortField() {
        // Given
        UserQueryWrapper queryWrapper = new UserQueryWrapper();
        queryWrapper.setSortField(null);
        queryWrapper.setSortOrder("ascend");

        // When
        QueryWrapper<User> result = userService.newQueryWrapper(queryWrapper);

        // Then
        assertNotNull(result);
        String sql = result.getSqlSegment();
        assertFalse(sql.contains("ORDER BY"));
    }

    @Test
    @DisplayName("测试 sortField 白名单校验 - 空字符串")
    void testNewQueryWrapper_EmptySortField() {
        // Given
        UserQueryWrapper queryWrapper = new UserQueryWrapper();
        queryWrapper.setSortField("");
        queryWrapper.setSortOrder("ascend");

        // When
        QueryWrapper<User> result = userService.newQueryWrapper(queryWrapper);

        // Then
        assertNotNull(result);
        String sql = result.getSqlSegment();
        assertFalse(sql.contains("ORDER BY"));
    }

    @Test
    @DisplayName("测试 sortField 白名单校验 - 所有允许的字段")
    void testNewQueryWrapper_AllAllowedFields() {
        // Given
        String[] allowedFields = {
            "id", "username", "email", "phone", "nickname", "role", "status", "level", "create_time", "update_time"
        };

        for (String field : allowedFields) {
            UserQueryWrapper queryWrapper = new UserQueryWrapper();
            queryWrapper.setSortField(field);
            queryWrapper.setSortOrder("ascend");

            // When
            QueryWrapper<User> result = userService.newQueryWrapper(queryWrapper);

            // Then
            assertNotNull(result);
            String sql = result.getSqlSegment();
            assertTrue(sql.contains("ORDER BY"), "字段 " + field + " 应该被允许");
            assertTrue(sql.contains(field), "字段 " + field + " 应该出现在 ORDER BY 中");
        }
    }

    @Test
    @DisplayName("测试 AdminGetUserVO 不包含 password 字段")
    void testAdminGetUserVO_NoPasswordField() {
        // Given
        AdminGetUserVO vo = new AdminGetUserVO();
        vo.setId(1L);
        vo.setUsername("testuser");
        vo.setNickname("测试用户");
        vo.setAvatar("https://example.com/avatar.jpg");
        vo.setEmail("test@example.com");
        vo.setPhone("13800138000");
        vo.setStatus(1);
        vo.setRole("user");
        vo.setLevel(0);

        // Then - 验证 AdminGetUserVO 没有 password 字段
        assertThrows(NoSuchMethodException.class, () -> {
            AdminGetUserVO.class.getMethod("getPassword");
        }, "AdminGetUserVO 不应该有 getPassword 方法");

        assertThrows(NoSuchMethodException.class, () -> {
            AdminGetUserVO.class.getMethod("setPassword", String.class);
        }, "AdminGetUserVO 不应该有 setPassword 方法");
    }
}
