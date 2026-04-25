package hk.ljx.fishpicsbackend.common.aop;

import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.JwtUtil;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.enums.UserRoleEnum;
import hk.ljx.fishpicsbackend.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.TimeUnit;


@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 执行拦截
     *
     * @param joinPoint 切入点
     * @param authCheck 权限校验注解
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.role();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 前端请求头带登录态
        String authorization = request.getHeader("Authorization");
        ExcUtils.throwIfTrue(authorization == null || JwtUtil.parseToken(authorization) == null, ExceptionCode.NOT_LOGIN);

        // 根据登录态查 redis 获取登录用户
        String loginByJson = stringRedisTemplate.opsForValue().get(authorization);
        ExcUtils.throwIfTrue(loginByJson == null || loginByJson.isEmpty(), ExceptionCode.NOT_LOGIN);
        User user = JSONUtil.toBean(loginByJson, User.class);

        // 如果用户为空则为未登录或登陆过期
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN, "未登录或登录过期");

        // 刷新 redis 有效期
        stringRedisTemplate.expire(authorization, 1, TimeUnit.DAYS);

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
