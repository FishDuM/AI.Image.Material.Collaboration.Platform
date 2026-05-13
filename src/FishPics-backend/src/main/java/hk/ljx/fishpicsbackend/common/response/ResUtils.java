package hk.ljx.fishpicsbackend.common.response;

import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;

import java.io.Serializable;

public class ResUtils implements Serializable {

    public static Response success(){
        return new Response(ExceptionCode.SUCCESS.getCode(), ExceptionCode.SUCCESS.getMessage(), null);
    }

    public static <T> Response<T> success(T data) {
        return new Response(ExceptionCode.SUCCESS.getCode(), ExceptionCode.SUCCESS.getMessage(), data);
    }

    public static <T> Response<T> successOfMessage(String message) {
        return new Response(ExceptionCode.SUCCESS.getCode(), message, null);
    }


    public static Response fail(BaseException be) {
        return new Response(be.getCode(), be.getMessage(), null);
    }

    public static Response fail(ExceptionCode ec, String message) {
        return new Response(ec.getCode(), message, null);
    }

    public static Response fail(RuntimeException re) {
        return new Response(0, re.getMessage(), null);
    }

    public static Response fail(String message) {
        return new Response(0, message, null);
    }
}
