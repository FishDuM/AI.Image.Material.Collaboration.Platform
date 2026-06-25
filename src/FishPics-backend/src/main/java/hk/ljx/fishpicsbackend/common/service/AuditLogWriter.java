package hk.ljx.fishpicsbackend.common.service;

import hk.ljx.fishpicsbackend.common.entity.SysAuditLog;
import hk.ljx.fishpicsbackend.mapper.SysAuditLogMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuditLogWriter {

    @Resource
    private SysAuditLogMapper sysAuditLogMapper;

    @Async
    public void saveAsync(SysAuditLog auditLog) {
        try {
            sysAuditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.error("保存审计日志失败: module={}, operation={}",
                    auditLog.getModule(), auditLog.getOperation(), e);
        }
    }
}
