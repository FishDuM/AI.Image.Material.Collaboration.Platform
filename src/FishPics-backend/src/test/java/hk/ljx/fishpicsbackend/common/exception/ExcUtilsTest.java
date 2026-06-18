package hk.ljx.fishpicsbackend.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ExcUtils 单元测试
 */
class ExcUtilsTest {

    // ==================== eq ====================

    @Test
    @DisplayName("eq - null 值应返回 false")
    void eq_null_returnsFalse() {
        assertFalse(ExcUtils.eq(null, 1));
    }

    @Test
    @DisplayName("eq - 相等值应返回 true")
    void eq_equal_returnsTrue() {
        assertTrue(ExcUtils.eq(1, 1));
        assertTrue(ExcUtils.eq(0, 0));
    }

    @Test
    @DisplayName("eq - 不相等值应返回 false")
    void eq_notEqual_returnsFalse() {
        assertFalse(ExcUtils.eq(1, 2));
        assertFalse(ExcUtils.eq(0, 1));
    }

    // ==================== error ====================

    @Test
    @DisplayName("error(ExceptionCode) - 应抛出 BaseException 并携带正确的 code 和 message")
    void error_withExceptionCode_throwsBaseException() {
        BaseException ex = assertThrows(BaseException.class, () -> ExcUtils.error(ExceptionCode.NOT_LOGIN));
        assertEquals(ExceptionCode.NOT_LOGIN.getCode(), ex.getCode());
        assertEquals(ExceptionCode.NOT_LOGIN.getMessage(), ex.getMessage());
    }

    @Test
    @DisplayName("error(ExceptionCode, String) - 应抛出 BaseException 并使用自定义 message")
    void error_withExceptionCodeAndMessage_throwsBaseException() {
        BaseException ex = assertThrows(BaseException.class,
                () -> ExcUtils.error(ExceptionCode.PARAMETER_ERROR, "自定义错误"));
        assertEquals(ExceptionCode.PARAMETER_ERROR.getCode(), ex.getCode());
        assertEquals("自定义错误", ex.getMessage());
    }

    @Test
    @DisplayName("error(Integer, String) - 应抛出 BaseException 并使用指定 code 和 message")
    void error_withCodeAndMessage_throwsBaseException() {
        BaseException ex = assertThrows(BaseException.class, () -> ExcUtils.error(40099, "数字错误码"));
        assertEquals(40099, ex.getCode());
        assertEquals("数字错误码", ex.getMessage());
    }

    // ==================== throwIfTrue ====================

    @Test
    @DisplayName("throwIfTrue - flag=true 时应抛异常")
    void throwIfTrue_true_throws() {
        assertThrows(BaseException.class, () -> ExcUtils.throwIfTrue(true, ExceptionCode.NOT_LOGIN));
    }

    @Test
    @DisplayName("throwIfTrue - flag=false 时不应抛异常")
    void throwIfTrue_false_noThrow() {
        assertDoesNotThrow(() -> ExcUtils.throwIfTrue(false, ExceptionCode.NOT_LOGIN));
    }

    @Test
    @DisplayName("throwIfTrue(flag, message) - flag=true 时使用 PARAMETER_ERROR")
    void throwIfTrue_withMessage_usesParameterError() {
        BaseException ex = assertThrows(BaseException.class,
                () -> ExcUtils.throwIfTrue(true, "参数错误"));
        assertEquals(ExceptionCode.PARAMETER_ERROR.getCode(), ex.getCode());
        assertEquals("参数错误", ex.getMessage());
    }

    @Test
    @DisplayName("throwIfTrue(flag, ExceptionCode, message) - 自定义 code 和 message")
    void throwIfTrue_withCodeAndMessage() {
        BaseException ex = assertThrows(BaseException.class,
                () -> ExcUtils.throwIfTrue(true, ExceptionCode.FORBIDDEN, "无权限"));
        assertEquals(ExceptionCode.FORBIDDEN.getCode(), ex.getCode());
        assertEquals("无权限", ex.getMessage());
    }

    // ==================== throwIfFalse ====================

    @Test
    @DisplayName("throwIfFalse - flag=false 时应抛异常")
    void throwIfFalse_false_throws() {
        assertThrows(BaseException.class, () -> ExcUtils.throwIfFalse(false, ExceptionCode.NOT_LOGIN));
    }

    @Test
    @DisplayName("throwIfFalse - flag=true 时不应抛异常")
    void throwIfFalse_true_noThrow() {
        assertDoesNotThrow(() -> ExcUtils.throwIfFalse(true, ExceptionCode.NOT_LOGIN));
    }

    @Test
    @DisplayName("throwIfFalse(flag, ExceptionCode, message) - 自定义 message")
    void throwIfFalse_withMessage() {
        BaseException ex = assertThrows(BaseException.class,
                () -> ExcUtils.throwIfFalse(false, ExceptionCode.INTERNAL_SERVER_ERROR, "数据库错误"));
        assertEquals(ExceptionCode.INTERNAL_SERVER_ERROR.getCode(), ex.getCode());
        assertEquals("数据库错误", ex.getMessage());
    }
}
