package hk.ljx.fishpicsbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import hk.ljx.fishpicsbackend.common.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志Mapper
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
