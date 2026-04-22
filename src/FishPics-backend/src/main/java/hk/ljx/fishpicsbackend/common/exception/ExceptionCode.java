package hk.ljx.fishpicsbackend.common.exception;

import lombok.Getter;

@Getter
public enum ExceptionCode {
    PARAMETER_ERROR(40001, "参数错误"),
    UNAUTHORIZED(40002, "未授权"),
    FORBIDDEN(40003, "禁止访问"),
    NOT_FOUND(40004, "资源未找到"),
    NOT_LOGIN(40005, "未登录"),
    INTERNAL_SERVER_ERROR(50000, "服务器内部错误"),
    UNPROCESSABLE_ENTITY(40022, "无法处理的实体"),
    TOO_MANY_REQUESTS(40029, "请求过多"),
    DATABASE_ERROR(50003, "数据库错误"),
    SERVICE_UNAVAILABLE(50001, "服务不可用");


    private Integer code;
    private String message;

    ExceptionCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
