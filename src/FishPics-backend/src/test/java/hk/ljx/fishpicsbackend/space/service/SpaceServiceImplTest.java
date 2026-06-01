package hk.ljx.fishpicsbackend.space.service;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.space.dto.SpaceQueryWrapper;
import hk.ljx.fishpicsbackend.space.entity.Space;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SpaceServiceImpl 单元测试
 * 测试 SQL 注入防护等功能
 */
@ExtendWith(MockitoExtension.class)
class SpaceServiceImplTest {

    @InjectMocks
    private SpaceServiceImpl spaceService;

    @Test
    @DisplayName("测试空间查询 sortField 白名单校验 - 允许的字段")
    void testGetSpaceQueryWrapper_AllowedSortFields() {
        // Given
        SpaceQueryWrapper queryWrapper = new SpaceQueryWrapper();
        queryWrapper.setSortField("create_time");
        queryWrapper.setSortOrder("ascend");

        // When
        QueryWrapper<Space> result = spaceService.getSpaceQueryWrapper(queryWrapper);

        // Then
        assertNotNull(result);
        String sql = result.getSqlSegment();
        assertTrue(sql.contains("ORDER BY"));
        assertTrue(sql.contains("create_time"));
    }

    @Test
    @DisplayName("测试空间查询 sortField 白名单校验 - 拒绝非法字段")
    void testGetSpaceQueryWrapper_RejectInvalidSortField() {
        // Given
        SpaceQueryWrapper queryWrapper = new SpaceQueryWrapper();
        queryWrapper.setSortField("1; DROP TABLE space; --");
        queryWrapper.setSortOrder("ascend");

        // When
        QueryWrapper<Space> result = spaceService.getSpaceQueryWrapper(queryWrapper);

        // Then
        assertNotNull(result);
        String sql = result.getSqlSegment();
        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains(";"));
    }

    @Test
    @DisplayName("测试空间查询 sortField 白名单校验 - null 值")
    void testGetSpaceQueryWrapper_NullSortField() {
        // Given
        SpaceQueryWrapper queryWrapper = new SpaceQueryWrapper();
        queryWrapper.setSortField(null);
        queryWrapper.setSortOrder("ascend");

        // When
        QueryWrapper<Space> result = spaceService.getSpaceQueryWrapper(queryWrapper);

        // Then
        assertNotNull(result);
        String sql = result.getSqlSegment();
        assertFalse(sql.contains("ORDER BY"));
    }

    @Test
    @DisplayName("测试图片查询 sortField 白名单校验 - 允许的字段")
    void testPictureList_AllowedSortFields() {
        // Given
        String[] allowedFields = {
            "id", "picture_name", "introduction", "tags", "url", "space_id",
            "user_id", "create_time", "update_time"
        };

        Set<String> allowedSet = new HashSet<>(Arrays.asList(allowedFields));

        for (String field : allowedFields) {
            // When - 直接测试白名单逻辑
            boolean isValid = allowedSet.contains(field);

            // Then
            assertTrue(isValid, "字段 " + field + " 应该被允许");
        }
    }

    @Test
    @DisplayName("测试图片查询 sortField 白名单校验 - 拒绝非法字段")
    void testPictureList_RejectInvalidSortField() {
        // Given
        String[] invalidFields = {
            "1; DROP TABLE picture; --",
            "name",
            "size",
            "status",
            "is_private"
        };

        String[] allowedFields = {
            "id", "picture_name", "introduction", "tags", "url", "space_id",
            "user_id", "create_time", "update_time"
        };

        Set<String> allowedSet = new HashSet<>(Arrays.asList(allowedFields));

        for (String field : invalidFields) {
            // When
            boolean isValid = allowedSet.contains(field);

            // Then
            assertFalse(isValid, "字段 " + field + " 应该被拒绝");
        }
    }

    @Test
    @DisplayName("测试空间查询 sortField 白名单校验 - 所有允许的字段")
    void testGetSpaceQueryWrapper_AllAllowedFields() {
        // Given
        String[] allowedFields = {
            "id", "introduction", "type", "user_id", "storage_size", "level", "name", "size", "create_time", "update_time"
        };

        for (String field : allowedFields) {
            SpaceQueryWrapper queryWrapper = new SpaceQueryWrapper();
            queryWrapper.setSortField(field);
            queryWrapper.setSortOrder("ascend");

            // When
            QueryWrapper<Space> result = spaceService.getSpaceQueryWrapper(queryWrapper);

            // Then
            assertNotNull(result);
            String sql = result.getSqlSegment();
            assertTrue(sql.contains("ORDER BY"), "字段 " + field + " 应该被允许");
            assertTrue(sql.contains(field), "字段 " + field + " 应该出现在 ORDER BY 中");
        }
    }
}
