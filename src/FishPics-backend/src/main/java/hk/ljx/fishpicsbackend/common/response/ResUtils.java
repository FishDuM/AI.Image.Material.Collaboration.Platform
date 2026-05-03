package hk.ljx.fishpicsbackend.common.response;

import hk.ljx.fishpicsbackend.common.exception.BaseException;

import java.io.Serializable;

public class ResUtils implements Serializable {

    public static Response success(){
        return new Response(1, "success", null);
    }

    public static <T> Response<T> success(T data) {
        return new Response(1, "success", data);
    }

    public static Response fail(BaseException be) {
        return new Response(0, be.getMessage(), null);
    }

    public static Response fail(RuntimeException re) {
        return new Response(0, re.getMessage(), null);
    }

    public static Response fail(String message) {
        return new Response(0, message, null);
    }
}
