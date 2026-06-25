package hk.ljx.fishpicsbackend.common.aop;

import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.common.annotation.AuditLog;
import hk.ljx.fishpicsbackend.common.entity.SysAuditLog;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.service.AuditLogWriter;
import hk.ljx.fishpicsbackend.common.utils.IpUtils;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
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

@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    @Resource
    private AuditLogWriter auditLogWriter;

    @Pointcut("@annotation(hk.ljx.fishpicsbackend.common.annotation.AuditLog)")
    public void auditLogPointcut() {}

    @Around("auditLogPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        SysAuditLog auditLog = new SysAuditLog();
        auditLog.setCreateTime(LocalDateTime.now());

        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            AuditLog auditLogAnnotation = method.getAnnotation(AuditLog.class);

            if (auditLogAnnotation != null) {
                auditLog.setModule(auditLogAnnotation.module());
                auditLog.setOperation(auditLogAnnotation.operation());
                auditLog.setDetail(auditLogAnnotation.description());
            }

            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                auditLog.setMethod(request.getMethod());
                auditLog.setUrl(request.getRequestURI());
                auditLog.setIp(IpUtils.getClientIp(request));
                String queryString = request.getQueryString();
                if (queryString != null && !queryString.isEmpty()) {
                    String maskedQuery = queryString
                            .replaceAll("(?i)(password|token|apiKey|secretKey|accessToken|refreshToken|secret|originalPassword)=[^&]*", "$1=***");
                    request.setAttribute("__auditQuery", maskedQuery);
                }
            }

            User user = UserHolder.getUser();
            if (user != null) {
                auditLog.setUserId(user.getId());
                auditLog.setUsername(user.getUsername());
            }

            try {
                Object[] args = joinPoint.getArgs();
                if (args != null && args.length > 0) {
                    StringBuilder params = new StringBuilder();
                    if (attributes != null) {
                        Object maskedQuery = attributes.getRequest().getAttribute("__auditQuery");
                        if (maskedQuery != null) {
                            params.append("?").append(maskedQuery).append(" ");
                        }
                    }
                    for (Object arg : args) {
                        if (arg != null && !arg.getClass().getName().startsWith("jakarta.servlet")) {
                            String json = JSONUtil.toJsonStr(arg);
                            json = json.replaceAll("\"password\":\"[^\"]*\"", "\"password\":\"***\"")
                                      .replaceAll("\"originalPassword\":\"[^\"]*\"", "\"originalPassword\":\"***\"")
                                      .replaceAll("\"token\":\"[^\"]*\"", "\"token\":\"***\"")
                                      .replaceAll("\"apiKey\":\"[^\"]*\"", "\"apiKey\":\"***\"")
                                      .replaceAll("\"secretKey\":\"[^\"]*\"", "\"secretKey\":\"***\"")
                                      .replaceAll("\"accessToken\":\"[^\"]*\"", "\"accessToken\":\"***\"")
                                      .replaceAll("\"refreshToken\":\"[^\"]*\"", "\"refreshToken\":\"***\"")
                                      .replaceAll("\"secret\":\"[^\"]*\"", "\"secret\":\"***\"");
                            // 截断过长参数（最大1000字符）
                            if (json.length() > 1000) {
                                json = json.substring(0, 1000) + "...(truncated)";
                            }
                            params.append(json).append(" ");
                        }
                    }
                    auditLog.setParams(params.toString().trim());
                }
            } catch (Exception e) {
                log.warn("获取请求参数失败", e);
            }

            Object result = joinPoint.proceed();

            auditLog.setResult(1);
            auditLogWriter.saveAsync(auditLog);

            return result;

        } catch (Throwable e) {
            auditLog.setResult(0);
            auditLog.setErrorMsg(e.getMessage());
            if (e instanceof BaseException) {
                log.warn("审计方法业务异常: method={}, msg={}", auditLog.getUrl(), e.getMessage());
            } else {
                log.error("审计方法执行异常: method={}", auditLog.getUrl(), e);
            }
            auditLogWriter.saveAsync(auditLog);

            throw e;
        }
    }

}
