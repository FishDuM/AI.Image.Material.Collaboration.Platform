package hk.ljx.fishpicsbackend.post.service;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.post.dto.PostQueryWrapper;
import hk.ljx.fishpicsbackend.post.entity.Post;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PostServiceImpl 单元测试
 * 测试 SQL 注入防护等功能
 */
@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @InjectMocks
    private PostServiceImpl postService;

    @Test
    @DisplayName("测试 sortField 白名单校验 - 允许的字段")
    void testNewQueryWrapper_AllowedSortFields() {
        // Given
        PostQueryWrapper queryWrapper = new PostQueryWrapper();
        queryWrapper.setSortField("create_time");
        queryWrapper.setSortOrder("asc");

        // When
        QueryWrapper<Post> result = postService.newQueryWrapper(queryWrapper);

        // Then
        assertNotNull(result);
        String sql = result.getSqlSegment();
        assertTrue(sql.contains("ORDER BY"));
        assertTrue(sql.contains("create_time"));
    }

    @Test
    @DisplayName("测试 sortField 白名单校验 - 拒绝非法字段")
    void testNewQueryWrapper_RejectInvalidSortField() {
        // Given
        PostQueryWrapper queryWrapper = new PostQueryWrapper();
        queryWrapper.setSortField("1; DROP TABLE post; --");
        queryWrapper.setSortOrder("asc");

        // When
        QueryWrapper<Post> result = postService.newQueryWrapper(queryWrapper);

        // Then
        assertNotNull(result);
        String sql = result.getSqlSegment();
        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains(";"));
    }

    @Test
    @DisplayName("测试 sortField 白名单校验 - null 值")
    void testNewQueryWrapper_NullSortField() {
        // Given
        PostQueryWrapper queryWrapper = new PostQueryWrapper();
        queryWrapper.setSortField(null);
        queryWrapper.setSortOrder("asc");

        // When
        QueryWrapper<Post> result = postService.newQueryWrapper(queryWrapper);

        // Then
        assertNotNull(result);
        String sql = result.getSqlSegment();
        assertFalse(sql.contains("ORDER BY"));
    }

    @Test
    @DisplayName("测试 sortField 白名单校验 - 空字符串")
    void testNewQueryWrapper_EmptySortField() {
        // Given
        PostQueryWrapper queryWrapper = new PostQueryWrapper();
        queryWrapper.setSortField("");
        queryWrapper.setSortOrder("asc");

        // When
        QueryWrapper<Post> result = postService.newQueryWrapper(queryWrapper);

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
            "id", "user_id", "title", "content", "cover", "status", "is_private",
            "likes_num", "collect_num", "comment_num", "views_num", "hot", "create_time", "update_time"
        };

        for (String field : allowedFields) {
            PostQueryWrapper queryWrapper = new PostQueryWrapper();
            queryWrapper.setSortField(field);
            queryWrapper.setSortOrder("asc");

            // When
            QueryWrapper<Post> result = postService.newQueryWrapper(queryWrapper);

            // Then
            assertNotNull(result);
            String sql = result.getSqlSegment();
            assertTrue(sql.contains("ORDER BY"), "字段 " + field + " 应该被允许");
            assertTrue(sql.contains(field), "字段 " + field + " 应该出现在 ORDER BY 中");
        }
    }
}
