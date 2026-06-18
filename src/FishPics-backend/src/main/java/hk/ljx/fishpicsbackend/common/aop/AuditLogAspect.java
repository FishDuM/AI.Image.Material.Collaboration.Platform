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

/**
 * 审计日志 AOP 切面，异步写入数据库。
 */
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
        // 提前记录请求时间，反映真实请求时间点
        auditLog.setCreateTime(LocalDateTime.now());

        try {
            // 获取注解信息
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            AuditLog auditLogAnnotation = method.getAnnotation(AuditLog.class);

            if (auditLogAnnotation != null) {
                auditLog.setModule(auditLogAnnotation.module());
                auditLog.setOperation(auditLogAnnotation.operation());
                auditLog.setDetail(auditLogAnnotation.description());
            }

            // 获取请求信息
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                auditLog.setMethod(request.getMethod());
                auditLog.setUrl(request.getRequestURI());
                auditLog.setIp(IpUtils.getClientIp(request));
                // GET query params 也需要脱敏
                String queryString = request.getQueryString();
                if (queryString != null && !queryString.isEmpty()) {
                    String maskedQuery = queryString
                            .replaceAll("(?i)(password|token|apiKey|secretKey|accessToken|refreshToken|secret|originalPassword)=[^&]*", "$1=***");
                    // 暂存到 request attribute,避免与 body args 冲突
                    request.setAttribute("__auditQuery", maskedQuery);
                }
            }

            // 获取当前用户
            User user = UserHolder.getUser();
            if (user != null) {
                auditLog.setUserId(user.getId());
                auditLog.setUsername(user.getUsername());
            }

            // 获取请求参数（过滤敏感字段，截断过长内容）
            try {
                // 把 query params 拼到 params 前面
                if (attributes != null) {
                    HttpServletRequest req = attributes.getRequest();
                    Object maskedQuery = req.getAttribute("__auditQuery");
                    if (maskedQuery != null) {
                        auditLog.setParams(maskedQuery.toString());
                    }
                }
                Object[] args = joinPoint.getArgs();
                if (args != null && args.length > 0) {
                    StringBuilder params = new StringBuilder();
                    // 把 query params 拼在前面
                    if (attributes != null) {
                        Object maskedQuery = attributes.getRequest().getAttribute("__auditQuery");
                        if (maskedQuery != null) {
                            params.append("?").append(maskedQuery).append(" ");
                        }
                    }
                    for (Object arg : args) {
                        if (arg != null && !arg.getClass().getName().startsWith("jakarta.servlet")) {
                            String json = JSONUtil.toJsonStr(arg);
                            // 过滤包含密码/token等敏感字段
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

            // 执行方法
            Object result = joinPoint.proceed();

            // 记录成功
            auditLog.setResult(1);
            auditLogWriter.saveAsync(auditLog);

            return result;

        } catch (Throwable e) {
            // 记录失败
            auditLog.setResult(0);
            auditLog.setErrorMsg(e.getMessage());
            // 业务异常（BaseException）用 WARN，系统异常用 ERROR
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
