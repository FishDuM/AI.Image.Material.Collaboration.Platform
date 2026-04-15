package hk.ljx.fishpicsbackend.common.exception;

import lombok.Getter;

@Getter
public class BaseException extends RuntimeException{

    /**
     * 错误码
     */
    private Integer code;

    public BaseException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
