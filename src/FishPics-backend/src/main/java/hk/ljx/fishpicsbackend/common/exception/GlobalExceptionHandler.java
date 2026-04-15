package hk.ljx.fishpicsbackend.common.exception;

import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public Response<?> handleException(BaseException be) {
        log.error("====> 业务异常,异常码：{},异常信息：{}", be.getCode(), be.getMessage());
        return ResUtils.fail(be);
    }

    @ExceptionHandler(RuntimeException.class)
    public Response<?> runtimeException(RuntimeException re) {
        log.error("====> 系统异常,异常信息：{}", re.getMessage());
        return ResUtils.fail(re);
    }
}
