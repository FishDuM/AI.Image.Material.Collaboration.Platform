package hk.ljx.fishpicsbackend.common.aop;

import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.common.annotation.AuditLog;
import hk.ljx.fishpicsbackend.common.entity.SysAuditLog;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.SysAuditLogMapper;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 审计日志AOP切面
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    @Resource
    private SysAuditLogMapper sysAuditLogMapper;

    @Pointcut("@annotation(hk.ljx.fishpicsbackend.common.annotation.AuditLog)")
    public void auditLogPointcut() {}

    @Around("auditLogPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        SysAuditLog auditLog = new SysAuditLog();

        try {
            // 获取注解信息
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            AuditLog auditLogAnnotation = method.getAnnotation(AuditLog.class);

            if (auditLogAnnotation != null) {
                auditLog.setModule(auditLogAnnotation.module());
                auditLog.setOperation(auditLogAnnotation.operation());
            }

            // 获取请求信息
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                auditLog.setMethod(request.getMethod());
                auditLog.setUrl(request.getRequestURI());
                auditLog.setIp(getClientIp(request));
            }

            // 获取当前用户
            User user = UserHolder.getUser();
            if (user != null) {
                auditLog.setUserId(user.getId());
                auditLog.setUsername(user.getUsername());
            }

            // 获取请求参数
            try {
                Object[] args = joinPoint.getArgs();
                if (args != null && args.length > 0) {
                    StringBuilder params = new StringBuilder();
                    for (Object arg : args) {
                        if (arg != null && !arg.getClass().getName().startsWith("jakarta.servlet")) {
                            params.append(JSONUtil.toJsonStr(arg)).append(" ");
                        }
                    }
                    auditLog.setParams(params.toString().trim());
                }
            } catch (Exception e) {
                log.warn("获取请求参数失败", e);
            }

            // 执行方法
            Object result = joinPoint.proceed();

            // 记录成功
            auditLog.setResult(1);
            auditLog.setCreateTime(LocalDateTime.now());
            saveAuditLog(auditLog);

            return result;

        } catch (Throwable e) {
            // 记录失败
            auditLog.setResult(0);
            auditLog.setErrorMsg(e.getMessage());
            auditLog.setCreateTime(LocalDateTime.now());
            saveAuditLog(auditLog);

            throw e;
        }
    }

    private void saveAuditLog(SysAuditLog auditLog) {
        try {
            sysAuditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.error("保存审计日志失败", e);
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
