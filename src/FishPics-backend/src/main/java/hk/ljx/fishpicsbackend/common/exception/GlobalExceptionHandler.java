package hk.ljx.fishpicsbackend.common.exception;

import com.alibaba.dashscope.exception.ApiException;
import hk.ljx.fishpicsbackend.common.response.Response;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final Map<String, String> DASHSCOPE_ERRORS = Map.of(
            "DataInspectionFailed", "生成的图片内容不合规",
            "IPInfringementSuspect", "输入提示词涉嫌侵权"
    );

    private static ResponseEntity<Response<?>> fail(ExceptionCode code, String message) {
        return ResponseEntity.status(ExceptionCode.toHttpStatus(code.getCode()))
                .body(Response.fail(new BaseException(code, message)));
    }

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<Response<?>> handleBusiness(BaseException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return fail(ExceptionCode.fromCode(e.getCode()), e.getMessage());
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Response<?>> handleDashScope(ApiException e) {
        log.error("DashScope API 异常", e);
        String msg = e.getMessage();
        if (msg != null) {
            for (var entry : DASHSCOPE_ERRORS.entrySet()) {
                if (msg.contains(entry.getKey())) {
                    return fail(ExceptionCode.AI_DRAW_ERROR, entry.getValue());
                }
            }
        }
        return fail(ExceptionCode.INTERNAL_SERVER_ERROR, "AI 服务调用失败");
    }

    // 所有参数校验类异常统一处理
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HttpRequestMethodNotSupportedException.class,
            HttpMediaTypeNotSupportedException.class,
            IllegalArgumentException.class,
            MaxUploadSizeExceededException.class
    })
    public ResponseEntity<Response<?>> handleParamError(Exception e) {
        String message;
        if (e instanceof MissingServletRequestParameterException ex) {
            message = "缺少参数: " + ex.getParameterName();
        } else if (e instanceof MethodArgumentTypeMismatchException ex) {
            message = "参数类型错误: " + ex.getName();
        } else if (e instanceof MethodArgumentNotValidException ex) {
            FieldError fieldError = ex.getBindingResult().getFieldError();
            message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数校验失败";
        } else if (e instanceof MaxUploadSizeExceededException) {
            message = "文件大小超出限制";
        } else if (e instanceof HttpRequestMethodNotSupportedException) {
            message = "请求方法不支持";
        } else if (e instanceof HttpMediaTypeNotSupportedException ex) {
            message = "不支持的 Content-Type: " + ex.getContentType();
        } else {
            message = e.getMessage() != null ? e.getMessage() : "参数错误";
        }
        log.warn("参数异常: {}", message);
        return fail(ExceptionCode.PARAMETER_ERROR, message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response<?>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "请求参数校验失败";
        log.warn("参数校验失败: {}", message);
        return fail(ExceptionCode.PARAMETER_ERROR, message);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Response<?>> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("数据重复: {}", e.getMessage());
        return fail(ExceptionCode.PARAMETER_ERROR, "数据重复，请检查后重试");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Response<?>> handle404(NoHandlerFoundException e) {
        log.warn("接口不存在: {} {}", e.getHttpMethod(), e.getRequestURL());
        return fail(ExceptionCode.NOT_FOUND, "接口不存在");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<?>> handleUnknown(Exception e, HttpServletRequest request) {
        log.error("未处理异常: uri={}", request.getRequestURI(), e);
        return fail(ExceptionCode.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后重试");
    }
}
