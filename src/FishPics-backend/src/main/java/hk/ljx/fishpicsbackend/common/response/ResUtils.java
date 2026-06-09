package hk.ljx.fishpicsbackend.common.response;

import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;

public class ResUtils {

    public static Response<?> success() {
        return new Response<>(ExceptionCode.SUCCESS.getCode(), ExceptionCode.SUCCESS.getMessage(), null);
    }

    public static <T> Response<T> success(T data) {
        return new Response<>(ExceptionCode.SUCCESS.getCode(), ExceptionCode.SUCCESS.getMessage(), data);
    }

    public static <T> Response<T> successOfMessage(String message) {
        return new Response<>(ExceptionCode.SUCCESS.getCode(), message, null);
    }

    public static Response<?> fail(BaseException be) {
        return new Response<>(be.getCode(), be.getMessage(), null);
    }

    public static Response<?> fail(String message) {
        return new Response<>(ExceptionCode.INTERNAL_SERVER_ERROR.getCode(), message, null);
    }
}
