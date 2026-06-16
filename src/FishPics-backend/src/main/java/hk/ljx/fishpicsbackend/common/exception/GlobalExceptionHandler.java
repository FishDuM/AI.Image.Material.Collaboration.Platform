package hk.ljx.fishpicsbackend.common.exception;

import com.alibaba.dashscope.exception.ApiException;
import hk.ljx.fishpicsbackend.common.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.dao.DuplicateKeyException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 根据 ExceptionCode 显式设 HTTP 状态,使基础设施能正确感知错误。
     */
    private static <T> ResponseEntity<Response<T>> wrap(Response<T> body) {
        HttpStatus status = ExceptionCode.toHttpStatus(body.getCode());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * 重载(用于返回类型 ResponseEntity&lt;Response&lt;?&gt;&gt; 的 handler)
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResponseEntity<Response<?>> wrapRaw(Response body) {
        HttpStatus status = ExceptionCode.toHttpStatus(body.getCode());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Response<?>> handleException(BaseException be) {
        // 业务异常用 WARN，不打印堆栈（这些是预期的校验错误，不是系统故障）
        log.warn("Business exception, code={}, message={}", be.getCode(), be.getMessage());
        return wrapRaw(Response.fail(be));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Response<?>> handleDashScopeError(ApiException e) {
        log.error("DashScope API exception", e);
        String msg = e.getMessage();
        String friendly = null;
        if (msg != null) {
            if (msg.contains("DataInspectionFailed")) friendly = "生成的图片内容不合规";
            else if (msg.contains("IPInfringementSuspect")) friendly = "输入提示词涉嫌侵权";
        }
        if (friendly != null) {
            return wrapRaw(Response.fail(new BaseException(ExceptionCode.AI_DRAW_ERROR, friendly)));
        }
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "AI 服务调用失败")));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Response<?>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.warn("Upload size exceeded: {}", e.getMessage());
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "文件大小超出限制")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<?>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数校验失败";
        log.warn("Validation failed: {}", message);
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, message)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Response<?>> handleMissingParameter(MissingServletRequestParameterException e) {
        String message = "缺少参数: " + e.getParameterName();
        log.warn("Missing request parameter: {}", message);
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, message)));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Response<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String message = "参数类型错误: " + e.getName();
        log.warn("Type mismatch: {}", message);
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, message)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response<?>> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("Request body parse failed: {}", e.getMessage());
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "请求体格式错误或字段类型不匹配")));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Response<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("Method not supported: {}", e.getMessage());
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "请求方法不支持")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Response<?>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        log.warn("Media type not supported: {}", e.getMessage());
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "不支持的 Content-Type: " + e.getContentType())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response<?>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, e.getMessage())));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Response<?>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("Duplicate key: {}", e.getMessage());
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "数据重复，请检查后重试")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<?>> runtimeException(Exception e, HttpServletRequest request) {
        log.error("Unhandled exception, uri={}", request.getRequestURI(), e);
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后重试")));
    }
}
