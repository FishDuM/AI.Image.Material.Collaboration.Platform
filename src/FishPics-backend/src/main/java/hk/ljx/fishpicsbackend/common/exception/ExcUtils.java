package hk.ljx.fishpicsbackend.common.exception;

public class ExcUtils {

    public static boolean eq(Integer value, int target) {
        return value != null && value == target;
    }

    public static void error(ExceptionCode exceptionCode) {
        throw new BaseException(exceptionCode.getCode(), exceptionCode.getMessage());
    }

    public static void error(ExceptionCode exceptionCode, String message) {
        throw new BaseException(exceptionCode.getCode(), message);
    }

    public static void error(Integer code, String message) {
        throw new BaseException(code, message);
    }

    public static void error(ExceptionCode exceptionCode, String message, Throwable cause) {
        throw new BaseException(exceptionCode, message, cause);
    }

    public static void throwIfTrue(boolean flag, ExceptionCode exceptionCode) {
        if (flag) {
            error(exceptionCode);
        }
    }

    public static void throwIfTrue(boolean flag, String message) {
        if (flag) {
            error(ExceptionCode.PARAMETER_ERROR.getCode(), message);
        }
    }

    public static void throwIfTrue(boolean flag, ExceptionCode exceptionCode, String message) {
        if (flag) {
            error(exceptionCode.getCode(), message);
        }
    }

    public static void throwIfFalse(boolean flag, ExceptionCode exceptionCode) {
        if (!flag) {
            error(exceptionCode);
        }
    }

    public static void throwIfFalse(boolean flag, ExceptionCode exceptionCode, String message) {
        if (!flag) {
            error(exceptionCode, message);
        }
    }
}
