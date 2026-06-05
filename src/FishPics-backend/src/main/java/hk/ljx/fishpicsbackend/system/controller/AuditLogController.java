package hk.ljx.fishpicsbackend.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.annotation.RequirePerm;
import hk.ljx.fishpicsbackend.common.entity.SysAuditLog;
import hk.ljx.fishpicsbackend.common.response.ResUtils;
import hk.ljx.fishpicsbackend.common.response.Response;
import hk.ljx.fishpicsbackend.system.dto.AuditLogQueryRequest;
import hk.ljx.fishpicsbackend.system.service.AuditLogService;
import hk.ljx.fishpicsbackend.system.vo.SystemStatsVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志 & 系统统计控制器
 */
@RestController
@RequestMapping("/system")
@Slf4j
public class AuditLogController {

    @Resource
    private AuditLogService auditLogService;

    /**
     * 分页查询审计日志
     */
    @RequirePerm("system:log:manage")
    @PostMapping("/audit-log/list")
    public Response<IPage<SysAuditLog>> auditLogList(@RequestBody AuditLogQueryRequest request) {
        return ResUtils.success(auditLogService.pageQuery(request));
    }

    /**
     * 获取系统统计概览
     */
    @RequirePerm("system:log:manage")
    @GetMapping("/stats")
    public Response<SystemStatsVO> stats() {
        return ResUtils.success(auditLogService.getStats());
    }
}
