package hk.ljx.fishpicsbackend.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.entity.SysAuditLog;
import hk.ljx.fishpicsbackend.system.dto.AuditLogQueryRequest;
import hk.ljx.fishpicsbackend.system.vo.SystemStatsVO;

/**
 * 审计日志 & 系统统计服务
 */
public interface AuditLogService {

    /**
     * 分页查询审计日志
     *
     * @param request 查询条件
     * @return 分页结果
     */
    IPage<SysAuditLog> pageQuery(AuditLogQueryRequest request);

    /**
     * 获取系统统计概览
     *
     * @return 统计数据
     */
    SystemStatsVO getStats();
}
