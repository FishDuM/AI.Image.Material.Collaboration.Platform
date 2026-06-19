package hk.ljx.fishpicsbackend.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import hk.ljx.fishpicsbackend.common.entity.SysAuditLog;
import hk.ljx.fishpicsbackend.system.dto.AuditLogQueryRequest;
import hk.ljx.fishpicsbackend.system.vo.SystemStatsVO;

public interface AuditLogService {

    IPage<SysAuditLog> pageQuery(AuditLogQueryRequest request);

    SystemStatsVO getStats();
}
