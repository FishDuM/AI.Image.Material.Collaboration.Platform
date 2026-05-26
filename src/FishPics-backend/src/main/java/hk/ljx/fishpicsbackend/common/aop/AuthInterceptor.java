package hk.ljx.fishpicsbackend.common.aop;

import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.user.User;
import hk.ljx.fishpicsbackend.common.enums.UserRoleEnum;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;


@Aspect
@Component
public class AuthInterceptor {

    /**
     * 执行拦截
     *
     * @param joinPoint 切入点
     * @param authCheck 权限校验注解
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.role();
        User user = UserHolder.getUser();
        // 如果用户为空则为未登录或登陆过期
        ExcUtils.throwIfTrue(user == null || user.getId() == null || user.getRole() == null, ExceptionCode.NOT_LOGIN, "未登录或登录过期");

        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByRole(mustRole);
        // 不需要权限，放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 以下为：必须有该权限才通过
        // 获取当前用户具有的权限
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByRole(user.getRole());
        // 没有权限，拒绝
        ExcUtils.throwIfTrue(userRoleEnum == null, ExceptionCode.UNAUTHORIZED);
        // 要求必须有管理员权限，但用户没有管理员权限，拒绝
        ExcUtils.throwIfTrue(UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum), ExceptionCode.UNAUTHORIZED);
        // 通过权限校验，放行
        return joinPoint.proceed();
    }
}
