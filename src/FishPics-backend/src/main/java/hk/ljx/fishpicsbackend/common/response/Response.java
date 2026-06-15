package hk.ljx.fishpicsbackend.common.response;

import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import lombok.Data;

@Data
public class Response<T> {

    private Integer code;

    private String message;

    private T data;

    public Response(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static Response<?> ok() {
        return new Response<>(ExceptionCode.SUCCESS.getCode(), ExceptionCode.SUCCESS.getMessage(), null);
    }

    public static <T> Response<T> ok(T data) {
        return new Response<>(ExceptionCode.SUCCESS.getCode(), ExceptionCode.SUCCESS.getMessage(), data);
    }

    public static <T> Response<T> okMsg(String message) {
        return new Response<>(ExceptionCode.SUCCESS.getCode(), message, null);
    }

    public static Response<?> fail(BaseException be) {
        return new Response<>(be.getCode(), be.getMessage(), null);
    }

    public static Response<?> fail(String message) {
        return new Response<>(ExceptionCode.INTERNAL_SERVER_ERROR.getCode(), message, null);
    }
}
