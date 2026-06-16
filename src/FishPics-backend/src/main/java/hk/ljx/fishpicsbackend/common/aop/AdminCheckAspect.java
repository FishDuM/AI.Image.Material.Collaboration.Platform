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

@Slf4j
@Aspect
@Component
public class AdminCheckAspect {

    @Around("@annotation(requireAdmin)")
    public Object doRequireAdmin(ProceedingJoinPoint joinPoint, RequireAdmin requireAdmin) throws Throwable {
        LoginContext ctx = UserHolder.getLoginContext();
        if (ctx == null || ctx.getUserId() == null) {
            throw new BaseException(ExceptionCode.NOT_LOGIN, "未登录或登录过期");
        }
        if (!ctx.isAdmin()) {
            log.warn("权限校验失败: userId={}, role={}, 需要管理员权限", ctx.getUserId(), ctx.getRole());
            throw new BaseException(ExceptionCode.FORBIDDEN, requireAdmin.message());
        }
        return joinPoint.proceed();
    }
}
