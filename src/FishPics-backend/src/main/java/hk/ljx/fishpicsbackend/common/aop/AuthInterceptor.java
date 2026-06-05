package hk.ljx.fishpicsbackend.common.aop;

import cn.hutool.core.util.StrUtil;
import hk.ljx.fishpicsbackend.common.annotation.AuthCheck;
import hk.ljx.fishpicsbackend.common.annotation.RequirePerm;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限拦截器（AOP）
 * 支持 @RequirePerm（新版）和 @AuthCheck（兼容旧版）
 * VIP 扩展权限支持 fallback：团队权限未命中时，检查 VIP 权限
 */
@Slf4j
@Aspect
@Component
public class AuthInterceptor {

    /**
     * VIP 扩展权限标识集合（不通过角色分配，由 user.level 自动生成）
     */
    private static final Set<String> VIP_EXT_PERMS = Set.of(
            "resource:upload:large",
            "resource:storage:expand",
            "resource:ai:quota"
    );

    /**
     * 拦截 @RequirePerm 注解（新版）
     */
    @Around("@annotation(requirePerm)")
    public Object doRequirePerm(ProceedingJoinPoint joinPoint, RequirePerm requirePerm) throws Throwable {
        // 1. 检查登录状态
        LoginContext ctx = UserHolder.getLoginContext();
        ExcUtils.throwIfTrue(ctx == null || ctx.getUserId() == null, ExceptionCode.NOT_LOGIN, "未登录或登录过期");

        // 2. 获取权限标识
        String permKey = requirePerm.value();
        if (StrUtil.isBlank(permKey)) {
            return joinPoint.proceed();
        }

        // 3. 根据权限前缀判断层级
        if (permKey.startsWith("system:")) {
            // 系统级权限：检查 systemPerms
            if (!ctx.hasSystemPerm(permKey)) {
                log.warn("权限校验失败: userId={}, perm={}, 类型=系统权限", ctx.getUserId(), permKey);
                throw new hk.ljx.fishpicsbackend.common.exception.BaseException(
                        ExceptionCode.FORBIDDEN, "无权限执行此操作");
            }
        } else if (permKey.startsWith("space:") || permKey.startsWith("resource:")) {
            // 团队级/资源级权限：需要 spaceId
            Long spaceId = getSpaceIdFromRequest(requirePerm.spaceIdParam());
            if (spaceId == null) {
                throw new hk.ljx.fishpicsbackend.common.exception.BaseException(
                        ExceptionCode.PARAMETER_ERROR, "缺少 spaceId 参数");
            }
            // 先检查团队权限
            if (ctx.hasTeamPerm(spaceId, permKey)) {
                return joinPoint.proceed();
            }
            // VIP 扩展权限 fallback：团队权限未命中时，检查 VIP 权限
            if (VIP_EXT_PERMS.contains(permKey) && ctx.hasVipPerm(permKey)) {
                return joinPoint.proceed();
            }
            log.warn("权限校验失败: userId={}, spaceId={}, perm={}, 类型=团队权限",
                    ctx.getUserId(), spaceId, permKey);
            throw new hk.ljx.fishpicsbackend.common.exception.BaseException(
                    ExceptionCode.FORBIDDEN, "无权限执行此操作");
        } else {
            // 未知前缀，拒绝
            log.error("未知权限标识前缀: perm={}", permKey);
            throw new hk.ljx.fishpicsbackend.common.exception.BaseException(
                    ExceptionCode.FORBIDDEN, "未知权限标识: " + permKey);
        }

        return joinPoint.proceed();
    }

    /**
     * 拦截 @AuthCheck 注解（兼容旧版）
     * 旧版只检查系统级权限
     */
    @Around("@annotation(authCheck)")
    public Object doAuthCheck(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 1. 检查登录状态
        LoginContext ctx = UserHolder.getLoginContext();
        ExcUtils.throwIfTrue(ctx == null || ctx.getUserId() == null, ExceptionCode.NOT_LOGIN, "未登录或登录过期");

        // 2. 获取注解中的权限配置
        String permissionStr = authCheck.permission();
        if (StrUtil.isBlank(permissionStr)) {
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

        // 4. 超管直接通过
        if (ctx.isSuperAdmin()) {
            return joinPoint.proceed();
        }

        // 5. 获取用户的系统级权限
        Set<String> userPermissions = ctx.getSystemPerms() != null
                ? Set.copyOf(ctx.getSystemPerms())
                : Set.of();

        // 6. 权限校验（兼容旧的 AND/OR 模式）
        boolean hasPermission;
        if (authCheck.mode() == AuthCheck.MatchMode.AND) {
            hasPermission = userPermissions.containsAll(requiredPermissions);
        } else {
            hasPermission = requiredPermissions.stream().anyMatch(userPermissions::contains);
        }

        if (!hasPermission) {
            log.warn("权限校验失败: userId={}, required={}, mode={}",
                    ctx.getUserId(), requiredPermissions, authCheck.mode());
            throw new hk.ljx.fishpicsbackend.common.exception.BaseException(
                    ExceptionCode.FORBIDDEN, "无权限执行此操作");
        }

        return joinPoint.proceed();
    }

    /**
     * 从请求中获取 spaceId
     * 优先从请求参数获取，其次从路径变量获取
     */
    @SuppressWarnings("unchecked")
    private Long getSpaceIdFromRequest(String paramName) {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();

            // 1. 先从请求参数获取
            String spaceIdStr = request.getParameter(paramName);
            if (StrUtil.isNotBlank(spaceIdStr)) {
                return Long.parseLong(spaceIdStr);
            }

            // 2. 从路径变量获取（Spring MVC 路径模板变量）
            Map<String, String> pathVars = (Map<String, String>) request.getAttribute(
                    HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            if (pathVars != null) {
                String pathValue = pathVars.get(paramName);
                if (StrUtil.isNotBlank(pathValue)) {
                    return Long.parseLong(pathValue);
                }
            }

            // 3. 尝试从请求体 JSON 中获取（需要请求已经被解析）
            // 注意：在拦截器阶段请求体可能已经被消费，这里只作为兜底

            return null;
        } catch (NumberFormatException e) {
            log.warn("spaceId 参数格式错误: paramName={}", paramName);
            return null;
        } catch (Exception e) {
            log.debug("获取 spaceId 失败: {}", e.getMessage());
            return null;
        }
    }
}
