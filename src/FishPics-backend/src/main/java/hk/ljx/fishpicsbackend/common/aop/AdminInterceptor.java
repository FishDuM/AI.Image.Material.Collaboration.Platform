package hk.ljx.fishpicsbackend.common.aop;

import hk.ljx.fishpicsbackend.common.annotation.RequireAdmin;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 简化的权限拦截器
 * 只需要判断 user.level >= 3 即为管理员
 */
@Slf4j
@Aspect
@Component
public class AdminInterceptor {

    /**
     * 拦截 @RequireAdmin 注解
     */
    @Around("@annotation(requireAdmin)")
    public Object doRequireAdmin(ProceedingJoinPoint joinPoint, RequireAdmin requireAdmin) throws Throwable {
        // 1. 检查登录状态
        LoginContext ctx = UserHolder.getLoginContext();
        if (ctx == null || ctx.getUserId() == null) {
            throw new BaseException(ExceptionCode.NOT_LOGIN, "未登录或登录过期");
        }

        // 2. 检查是否是管理员（level >= 3）
        if (!ctx.isAdmin()) {
            log.warn("权限校验失败: userId={}, level={}, 需要管理员权限", ctx.getUserId(), ctx.getLevel());
            throw new BaseException(ExceptionCode.FORBIDDEN, requireAdmin.message());
        }

        // 3. 权限校验通过，继续执行
        return joinPoint.proceed();
    }
}
