package hk.ljx.fishpicsbackend.common.exception;

import com.alibaba.dashscope.exception.ApiException;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public Response<?> handleException(BaseException be) {
        // 业务异常用 WARN，不打印堆栈（这些是预期的校验错误，不是系统故障）
        log.warn("Business exception, code={}, message={}", be.getCode(), be.getMessage());
        return ResUtils.fail(be);
    }

    @ExceptionHandler(ApiException.class)
    public Response<?> handleDashScopeError(ApiException e) {
        log.error("DashScope API exception", e);
        String friendly = ExcUtils.translateDashScopeError(e);
        if (friendly != null) {
            return ResUtils.fail(new BaseException(ExceptionCode.AI_DRAW_ERROR, friendly));
        }
        return ResUtils.fail(new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "AI 服务调用失败"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Response<?> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("Upload size exceeded: {}", e.getMessage());
        return ResUtils.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "文件大小超出限制"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Response<?> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数校验失败";
        log.warn("Validation failed: {}", message);
        return ResUtils.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, message));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Response<?> handleMissingParameter(MissingServletRequestParameterException e) {
        String message = "缺少参数: " + e.getParameterName();
        log.warn("Missing request parameter: {}", message);
        return ResUtils.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Response<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "参数类型错误: " + e.getName();
        log.warn("Type mismatch: {}", message);
        return ResUtils.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Response<?> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("Request body parse failed: {}", e.getMessage());
        return ResUtils.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "请求体格式错误或字段类型不匹配"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Response<?> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not supported: {}", e.getMessage());
        return ResUtils.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "请求方法不支持"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Response<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return ResUtils.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public Response<?> runtimeException(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception, uri={}", request.getRequestURI(), e);
        return ResUtils.fail(new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后重试"));
    }
}
