package hk.ljx.fishpicsbackend.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExceptionCode 单元测试
 * 测试错误码定义和修复
 */
class ExceptionCodeTest {

    @Test
    @DisplayName("测试错误码唯一性 - AI_TAG_ERROR 和 AI_DRAW_ERROR 不应重复")
    void testErrorCode_Uniqueness() {
        // Given
        ExceptionCode aiTagError = ExceptionCode.AI_TAG_ERROR;
        ExceptionCode aiDrawError = ExceptionCode.AI_DRAW_ERROR;

        // Then
        assertNotEquals(aiTagError.getCode(), aiDrawError.getCode(),
            "AI_TAG_ERROR 和 AI_DRAW_ERROR 不应使用相同的错误码");
    }

    @Test
    @DisplayName("测试错误码值 - AI_TAG_ERROR 应为 40006")
    void testErrorCode_AiTagError() {
        // Given
        ExceptionCode aiTagError = ExceptionCode.AI_TAG_ERROR;

        // Then
        assertEquals(40006, aiTagError.getCode());
        assertEquals("AI生成图片标签失败", aiTagError.getMessage());
    }

    @Test
    @DisplayName("测试错误码值 - AI_DRAW_ERROR 应为 40007")
    void testErrorCode_AiDrawError() {
        // Given
        ExceptionCode aiDrawError = ExceptionCode.AI_DRAW_ERROR;

        // Then
        assertEquals(40007, aiDrawError.getCode());
        assertEquals("AI生成图片失败", aiDrawError.getMessage());
    }

    @Test
    @DisplayName("测试所有错误码唯一性")
    void testErrorCode_AllCodesUnique() {
        // Given
        ExceptionCode[] allCodes = ExceptionCode.values();

        // When & Then
        for (int i = 0; i < allCodes.length; i++) {
            for (int j = i + 1; j < allCodes.length; j++) {
                assertNotEquals(allCodes[i].getCode(), allCodes[j].getCode(),
                    "错误码 " + allCodes[i].name() + " 和 " + allCodes[j].name() + " 不应相同");
            }
        }
    }

    @Test
    @DisplayName("测试错误码范围 - 参数错误类应在 40000-49999")
    void testErrorCode_ParameterErrors() {
        // Given
        ExceptionCode[] parameterErrors = {
            ExceptionCode.PARAMETER_ERROR,
            ExceptionCode.UNAUTHORIZED,
            ExceptionCode.FORBIDDEN,
            ExceptionCode.NOT_FOUND,
            ExceptionCode.NOT_LOGIN,
            ExceptionCode.AI_TAG_ERROR,
            ExceptionCode.AI_DRAW_ERROR
        };

        // Then
        for (ExceptionCode code : parameterErrors) {
            assertTrue(code.getCode() >= 40000 && code.getCode() < 50000,
                "错误码 " + code.name() + " 应在 40000-49999 范围内，实际值: " + code.getCode());
        }
    }

    @Test
    @DisplayName("测试错误码范围 - 服务器错误类应在 50000-59999")
    void testErrorCode_ServerErrors() {
        // Given
        ExceptionCode[] serverErrors = {
            ExceptionCode.INTERNAL_SERVER_ERROR,
            ExceptionCode.SERVICE_UNAVAILABLE,
            ExceptionCode.DATABASE_ERROR
        };

        // Then
        for (ExceptionCode code : serverErrors) {
            assertTrue(code.getCode() >= 50000 && code.getCode() < 60000,
                "错误码 " + code.name() + " 应在 50000-59999 范围内，实际值: " + code.getCode());
        }
    }

    @Test
    @DisplayName("测试错误码字段 - code 和 message 不应为 null")
    void testErrorCode_FieldsNotNull() {
        // Given
        ExceptionCode[] allCodes = ExceptionCode.values();

        // Then
        for (ExceptionCode code : allCodes) {
            assertNotNull(code.getCode(), "错误码 " + code.name() + " 的 code 不应为 null");
            assertNotNull(code.getMessage(), "错误码 " + code.name() + " 的 message 不应为 null");
            assertFalse(code.getMessage().isEmpty(), "错误码 " + code.name() + " 的 message 不应为空");
        }
    }

    @Test
    @DisplayName("测试错误码枚举 - 获取所有枚举值")
    void testErrorCode_GetAllValues() {
        // When
        ExceptionCode[] values = ExceptionCode.values();

        // Then
        assertNotNull(values);
        assertTrue(values.length > 0, "ExceptionCode 应该有至少一个枚举值");
        assertTrue(values.length >= 13, "ExceptionCode 应该至少有 13 个枚举值");
    }

    @Test
    @DisplayName("测试错误码枚举 - valueOf 方法")
    void testErrorCode_ValueOf() {
        // When & Then
        assertEquals(ExceptionCode.SUCCESS, ExceptionCode.valueOf("SUCCESS"));
        assertEquals(ExceptionCode.PARAMETER_ERROR, ExceptionCode.valueOf("PARAMETER_ERROR"));
        assertEquals(ExceptionCode.AI_TAG_ERROR, ExceptionCode.valueOf("AI_TAG_ERROR"));
        assertEquals(ExceptionCode.AI_DRAW_ERROR, ExceptionCode.valueOf("AI_DRAW_ERROR"));
    }

    @Test
    @DisplayName("测试错误码枚举 - valueOf 方法对不存在的名称抛出异常")
    void testErrorCode_ValueOf_InvalidName() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            ExceptionCode.valueOf("NON_EXISTENT_CODE");
        });
    }
}
