package hk.ljx.fishpicsbackend.common.aop;

import cn.hutool.json.JSONUtil;
import hk.ljx.fishpicsbackend.common.annotation.AuditLog;
import hk.ljx.fishpicsbackend.common.entity.SysAuditLog;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.utils.IpUtils;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.SysAuditLogMapper;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 审计日志AOP切面（异步写入，通过 RocketMQ 解耦）
 */
@Slf4j
@Aspect
@Component
public class AuditLogAspect {

    @Resource
    private SysAuditLogMapper sysAuditLogMapper;

    @Resource(name = "auditLogProducer")
    private DefaultMQProducer auditLogProducer;

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
            sendAuditLogAsync(auditLog);

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
            sendAuditLogAsync(auditLog);

            throw e;
        }
    }

    /**
     * 异步发送审计日志到 RocketMQ
     * 用独立线程池异步执行，MQ 挂了就降级成 DB 写
     */
    private final java.util.concurrent.ExecutorService auditLogExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "audit-log-fallback");
                t.setDaemon(true);
                return t;
            });

    private void sendAuditLogAsync(SysAuditLog auditLog) {
        auditLogExecutor.execute(() -> {
            try {
                String json = JSONUtil.toJsonStr(auditLog);
                Message msg = new Message("audit-log-topic", json.getBytes(StandardCharsets.UTF_8));
                SendResult sendResult = auditLogProducer.send(msg);
                if (sendResult.getSendStatus() != SendStatus.SEND_OK) {
                    log.warn("审计日志MQ发送非OK，降级为同步写DB: status={}", sendResult.getSendStatus());
                    saveAuditLogDirect(auditLog);
                }
            } catch (Exception e) {
                log.warn("审计日志MQ发送失败，降级为同步写DB", e);
                saveAuditLogDirect(auditLog);
            }
        });
    }

    /**
     * 降级：直接同步写 DB
     */
    private void saveAuditLogDirect(SysAuditLog auditLog) {
        try {
            sysAuditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.error("保存审计日志失败", e);
        }
    }
}
