package hk.ljx.fishpicsbackend.common.exception;

import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public Response<?> handleException(BaseException be) {
        log.error("====> 业务异常,异常码：{},异常信息：{}", be.getCode(), be.getMessage());
        return ResUtils.fail(be);
    }

    @ExceptionHandler(RuntimeException.class)
    public Response runtimeException(
            RuntimeException e,
            HttpServletRequest request,
            HttpServletResponse response) {

        // 关键：如果是图片验证码请求，直接返回 null，不处理！
        String uri = request.getRequestURI();
        if (uri.contains("captcha") || uri.contains("checkCode")) {
            return null; // 直接返回null，不输出JSON，不操作流
        }

        // 其他接口正常返回JSON
        return ResUtils.fail(e.getMessage());
    }
}
