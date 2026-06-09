package hk.ljx.fishpicsbackend.common.exception;

import com.alibaba.dashscope.exception.ApiException;

public class ExcUtils {
    public static BaseException error(ExceptionCode exceptionCode) {
        throw new BaseException(exceptionCode.getCode(), exceptionCode.getMessage());
    }

    public static BaseException error(ExceptionCode exceptionCode, String message) {
        throw new BaseException(exceptionCode.getCode(), message);
    }

    public static BaseException error(Integer code, String message) {
        throw new BaseException(code, message);
    }

    public static void throwIfTrue(Boolean flag, ExceptionCode exceptionCode) {
        if (flag == null || flag) {
            throw error(exceptionCode);
        }
    }

    public static void throwIfTrue(Boolean flag, String message) {
        if (flag == null || flag) {
            throw error(ExceptionCode.PARAMETER_ERROR.getCode(), message);
        }
    }

    public static void throwIfTrue(Boolean flag, ExceptionCode exceptionCode, String message) {
        if (flag == null || flag) {
            throw error(exceptionCode.getCode(), message);
        }
    }

    public static void throwIfFalse(Boolean flag, ExceptionCode exceptionCode) {
        if (flag == null || !flag) {
            throw error(exceptionCode);
        }
    }

    public static void throwIfFalse(Boolean flag, ExceptionCode exceptionCode, String message) {
        if (flag == null || !flag) {
            throw error(exceptionCode, message);
        }
    }

    /**
     * 将 DashScope ApiException 的错误码翻译为友好提示
     * @return 友好提示，无匹配返回 null
     */
    public static String translateDashScopeError(ApiException e) {
        String msg = e.getMessage();
        if (msg == null) return null;
        if (msg.contains("DataInspectionFailed")) return "生成的图片内容不合规";
        if (msg.contains("IPInfringementSuspect")) return "输入提示词涉嫌侵权";
        return null;
    }
}
