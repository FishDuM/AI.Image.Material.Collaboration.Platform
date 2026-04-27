package hk.ljx.fishpicsbackend.common.exception;

public class ExcUtils {
    public static BaseException error(ExceptionCode exceptionCode) {
        throw new BaseException(exceptionCode.getCode(), exceptionCode.getMessage());
    }

    public static BaseException error(ExceptionCode exceptionCode, String message) {
        throw new BaseException(exceptionCode.getCode(), message);
    }

    public static BaseException error(Integer code, String message) {
        return new BaseException(code, message);
    }

    public static void throwIfTrue(Boolean flag, ExceptionCode exceptionCode) {
        if (flag) {
            throw error(exceptionCode);
        }
    }

    public static void throwIfTrue(Boolean flag, String message) {
        if (flag) {
            throw error(1,message);
        }
    }

    public static void throwIfTrue(Boolean flag, ExceptionCode exceptionCode, String message) {
        if (flag) {
            throw error(exceptionCode.getCode(), message);
        }
    }

    public static void throwIfFalse(Boolean flag, ExceptionCode exceptionCode) {
        if (!flag) {
            throw error(exceptionCode);
        }
    }
}
