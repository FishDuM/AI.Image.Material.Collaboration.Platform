package hk.ljx.fishpicsbackend.common.interceptor;

import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器
 * 检查 ThreadLocal 中是否有登录上下文
 *
 * 执行顺序：order=1（在 TokenRefreshInterceptor 之后）
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        LoginContext ctx = UserHolder.getLoginContext();
        if (ctx == null || ctx.getUserId() == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(JSONUtil.toJsonStr(
                    new Response<>(ExceptionCode.NOT_LOGIN.getCode(), "未登录或登录已过期", null)));
            return false;
        }
        return true;
    }
}
