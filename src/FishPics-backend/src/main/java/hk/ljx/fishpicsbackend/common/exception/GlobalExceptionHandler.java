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
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.dao.DuplicateKeyException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static <T> ResponseEntity<Response<T>> wrap(Response<T> body) {
        HttpStatus status = ExceptionCode.toHttpStatus(body.getCode());
        return ResponseEntity.status(status).body(body);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ResponseEntity<Response<?>> wrapRaw(Response body) {
        HttpStatus status = ExceptionCode.toHttpStatus(body.getCode());
        return ResponseEntity.status(status).body(body);
    }

    // 业务异常，不打堆栈，避免日志刷屏
    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Response<?>> handleException(BaseException be) {
        log.warn("Business exception, code={}, message={}", be.getCode(), be.getMessage());
        return wrapRaw(Response.fail(be));
    }

    // DashScope 调用失败，需要区分内容审核和真正的 API 错误
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
        log.warn("上传文件超限: {}", e.getMessage());
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "文件大小超出限制")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<?>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数校验失败";
        log.warn("参数校验失败: {}", message);
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, message)));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Response<?>> handleMissingParam(MissingServletRequestParameterException e) {
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "缺少参数: " + e.getParameterName())));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Response<?>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "参数类型错误: " + e.getName())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response<?>> handleBodyParseError(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "请求体格式错误或字段类型不匹配")));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Response<?>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e) {
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "请求方法不支持")));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Response<?>> handleMediaType(HttpMediaTypeNotSupportedException e) {
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "不支持的 Content-Type: " + e.getContentType())));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response<?>> handleIllegalArg(IllegalArgumentException e) {
        log.warn("非法参数: {}", e.getMessage());
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, e.getMessage())));
    }

    // 唯一键冲突，比如重复注册
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Response<?>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("数据重复: {}", e.getMessage());
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.PARAMETER_ERROR, "数据重复，请检查后重试")));
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Response<?>> handle404(NoHandlerFoundException e, HttpServletRequest request) {
        log.warn("接口不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.NOT_FOUND, "接口不存在")));
    }

    // 兜底：没被上面捕获的异常都走这里
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<?>> handleUnknown(Exception e, HttpServletRequest request) {
        log.error("未处理异常, uri={}", request.getRequestURI(), e);
        return wrapRaw(Response.fail(new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后重试")));
    }
}
