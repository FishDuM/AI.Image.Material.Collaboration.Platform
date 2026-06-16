package hk.ljx.fishpicsbackend.common.aop;

import hk.ljx.fishpicsbackend.common.annotation.RequireLogin;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoginCheckAspect {

    @Around("@annotation(requireLogin)")
    public Object doRequireLogin(ProceedingJoinPoint joinPoint, RequireLogin requireLogin) throws Throwable {
        LoginContext ctx = UserHolder.getLoginContext();
        if (ctx == null || ctx.getUserId() == null) {
            throw new BaseException(ExceptionCode.NOT_LOGIN, "未登录或登录过期");
        }
        return joinPoint.proceed();
    }
}
