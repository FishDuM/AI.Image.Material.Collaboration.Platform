package hk.ljx.fishpicsbackend.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExceptionCode {
    SUCCESS(1, "success"),
    PARAMETER_ERROR(40001, "参数错误"),
    UNAUTHORIZED(40002, "无权限"),
    FORBIDDEN(40003, "禁止访问"),
    CONFLICT(40009, "操作冲突"),
    NOT_FOUND(40004, "资源未找到"),
    NOT_LOGIN(40005, "未登录"),
    UNPROCESSABLE_ENTITY(40022, "无法处理的实体"),
    TOO_MANY_REQUESTS(40029, "请求过多"),
    INTERNAL_SERVER_ERROR(50000, "服务器内部错误"),
    DATABASE_ERROR(50003, "数据库错误"),
    AI_TAG_ERROR(40006, "AI生成图片标签失败"),
    AI_DRAW_ERROR(40007, "AI生成图片失败"),
    SERVICE_UNAVAILABLE(50001, "服务不可用");

    private final Integer code;
    private final String message;

    ExceptionCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 把业务 code 映射到 HTTP 状态
     * 4xxxx → 400, 500xx → 500, 其他 → 200
     */
    public static HttpStatus toHttpStatus(int code) {
        if (code == 1) {
            return HttpStatus.OK;
        }
        if (code >= 50000) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (code >= 40000) {
            return switch (code) {
                case 40002, 40005 -> HttpStatus.UNAUTHORIZED;
                case 40003 -> HttpStatus.FORBIDDEN;
                case 40004 -> HttpStatus.NOT_FOUND;
                case 40009 -> HttpStatus.CONFLICT;
                case 40022 -> HttpStatus.UNPROCESSABLE_ENTITY;
                case 40029 -> HttpStatus.TOO_MANY_REQUESTS;
                default -> HttpStatus.BAD_REQUEST;
            };
        }
        return HttpStatus.OK;
    }
}
