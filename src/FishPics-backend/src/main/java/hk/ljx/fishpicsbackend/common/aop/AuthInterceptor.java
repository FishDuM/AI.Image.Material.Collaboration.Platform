package hk.ljx.fishpicsbackend.common.aop;

import cn.hutool.core.util.StrUtil;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.permission.service.PermissionService;
import hk.ljx.fishpicsbackend.user.entity.User;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限拦截器（AOP）
 * 基于权限码校验，替代旧的基于角色名校验
 */
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private PermissionService permissionService;

    // 执行拦截
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 1. 检查登录状态
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null || user.getId() == null, ExceptionCode.NOT_LOGIN, "未登录或登录过期");

        // 2. 获取注解中的权限配置
        String permissionStr = authCheck.permission();
        if (StrUtil.isBlank(permissionStr)) {
            // 没有权限要求，已登录即可放行
            return joinPoint.proceed();
        }

        // 3. 解析权限码（支持逗号分隔多个权限）
        List<String> requiredPermissions = Arrays.stream(permissionStr.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());

        if (requiredPermissions.isEmpty()) {
            return joinPoint.proceed();
        }

        // 4. 获取用户的系统级权限
        Set<String> userPermissions = permissionService.getUserPermissions(user.getId());

        // 5. 权限校验
        boolean hasPermission;
        if (authCheck.mode() == AuthCheck.MatchMode.AND) {
            // 需要所有权限
            hasPermission = userPermissions.containsAll(requiredPermissions);
        } else {
            // 拥有任一即可
            hasPermission = requiredPermissions.stream().anyMatch(userPermissions::contains);
        }

        ExcUtils.throwIfTrue(!hasPermission, ExceptionCode.UNAUTHORIZED, "无权限执行此操作");

        // 6. 通过
        return joinPoint.proceed();
    }
}
