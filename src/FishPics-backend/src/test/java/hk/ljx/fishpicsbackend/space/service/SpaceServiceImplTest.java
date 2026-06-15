package hk.ljx.fishpicsbackend.space.service;

import cn.hutool.core.util.ReflectUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.space.dto.SpaceQueryWrapper;
import hk.ljx.fishpicsbackend.space.entity.Space;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceServiceImplTest {

    private final SpaceServiceImpl spaceService = new SpaceServiceImpl();

    @SuppressWarnings("unchecked")
    private QueryWrapper<Space> invokeGetSpaceQueryWrapper(SpaceQueryWrapper request) {
        Method method = ReflectUtil.getMethod(SpaceServiceImpl.class, "getSpaceQueryWrapper", SpaceQueryWrapper.class);
        assertNotNull(method);
        return (QueryWrapper<Space>) ReflectUtil.invoke(spaceService, method, request);
    }

    @Test
    @DisplayName("getSpaceQueryWrapper 应允许白名单排序字段")
    void getSpaceQueryWrapperShouldAllowWhitelistedSortField() {
        SpaceQueryWrapper request = new SpaceQueryWrapper();
        request.setSortField("create_time");
        request.setSortOrder("ascend");

        QueryWrapper<Space> result = invokeGetSpaceQueryWrapper(request);

        String sql = result.getSqlSegment();
        assertNotNull(sql);
        assertTrue(sql.contains("ORDER BY"));
        assertTrue(sql.contains("create_time"));
    }

    @Test
    @DisplayName("getSpaceQueryWrapper 应拦截非法排序字段")
    void getSpaceQueryWrapperShouldRejectIllegalSortField() {
        SpaceQueryWrapper request = new SpaceQueryWrapper();
        request.setSortField("1; DROP TABLE space; --");
        request.setSortOrder("ascend");

        QueryWrapper<Space> result = invokeGetSpaceQueryWrapper(request);

        String sql = result.getSqlSegment();
        assertFalse(sql.contains("DROP TABLE"));
        assertFalse(sql.contains(";"));
        assertFalse(sql.contains("ORDER BY"));
    }

    @Test
    @DisplayName("getSpaceQueryWrapper 空排序字段时不应追加排序")
    void getSpaceQueryWrapperShouldIgnoreNullSortField() {
        SpaceQueryWrapper request = new SpaceQueryWrapper();
        request.setSortField(null);
        request.setSortOrder("ascend");

        QueryWrapper<Space> result = invokeGetSpaceQueryWrapper(request);

        String sql = result.getSqlSegment();
        if (sql != null) {
            assertFalse(sql.contains("ORDER BY"));
        }
    }

    @Test
    @DisplayName("图片排序字段白名单应覆盖允许字段")
    void pictureSortWhitelistShouldContainAllowedFields() {
        String[] allowedFields = {
                "id", "picture_name", "introduction", "url", "space_id",
                "user_id", "create_time", "update_time"
        };

        Set<String> allowedSet = new HashSet<>(Arrays.asList(allowedFields));
        for (String field : allowedFields) {
            assertTrue(allowedSet.contains(field), "字段 " + field + " 应该被允许");
        }
    }

    @Test
    @DisplayName("图片排序字段白名单应拒绝非法字段")
    void pictureSortWhitelistShouldRejectIllegalFields() {
        String[] invalidFields = {
                "1; DROP TABLE picture; --",
                "name",
                "size",
                "status",
                "is_private"
        };

        String[] allowedFields = {
                "id", "picture_name", "introduction", "url", "space_id",
                "user_id", "create_time", "update_time"
        };

        Set<String> allowedSet = new HashSet<>(Arrays.asList(allowedFields));
        for (String field : invalidFields) {
            assertFalse(allowedSet.contains(field), "字段 " + field + " 应该被拒绝");
        }
    }

    @Test
    @DisplayName("getSpaceQueryWrapper 应允许所有白名单字段")
    void getSpaceQueryWrapperShouldAllowAllWhitelistedFields() {
        String[] allowedFields = {
                "id", "introduction", "type", "user_id", "storage_size", "level", "name", "size", "create_time", "update_time"
        };

        for (String field : allowedFields) {
            SpaceQueryWrapper request = new SpaceQueryWrapper();
            request.setSortField(field);
            request.setSortOrder("ascend");

            QueryWrapper<Space> result = invokeGetSpaceQueryWrapper(request);

            String sql = result.getSqlSegment();
            assertNotNull(sql);
            assertTrue(sql.contains("ORDER BY"), "字段 " + field + " 应该被允许");
            assertTrue(sql.contains(field), "字段 " + field + " 应该出现在 ORDER BY 中");
        }
    }
}
