package hk.ljx.fishpicsbackend.common.aop;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.enums.UserRoleEnum;
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

import static hk.ljx.fishpicsbackend.common.constants.RedisConstants.TOKEN_KEY;
import static hk.ljx.fishpicsbackend.common.constants.RedisConstants.USER_ID_KEY;


@Aspect
@Component
public class AuthInterceptor {

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

        Object attribute = request.getSession().getAttribute(TOKEN_KEY);
        String userId = null;
        if (attribute != null){
            userId = attribute.toString();
        }

        // 如果用户为空则为未登录或登陆过期
        ExcUtils.throwIfTrue(userId == null, ExceptionCode.NOT_LOGIN, "未登录或登录过期");


        String userJson = stringRedisTemplate.opsForValue().get(USER_ID_KEY + userId);
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(userJson), ExceptionCode.UNAUTHORIZED, "用户未登录");
        User user = JSONUtil.toBean(userJson, User.class);
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
