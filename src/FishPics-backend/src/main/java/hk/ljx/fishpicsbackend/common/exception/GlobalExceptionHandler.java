package hk.ljx.fishpicsbackend.common.exception;

import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public Response<?> handleException(BaseException be) {
        log.error("====> 业务异常,异常码：{},异常信息：{}", be.getCode(), be.getMessage());
        return ResUtils.fail(be);
    }

    @ExceptionHandler(Exception.class)
    public Response<?> runtimeException(
            Exception e,
            HttpServletRequest request) {

        log.error("====> 系统异常, 请求路径：{}, 异常信息：", request.getRequestURI(), e);

        return ResUtils.fail(new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "服务器内部错误，请稍后重试"));
    }
}
