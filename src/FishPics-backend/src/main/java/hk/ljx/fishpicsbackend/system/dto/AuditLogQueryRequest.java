package hk.ljx.fishpicsbackend.system.dto;

import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 审计日志查询条件
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AuditLogQueryRequest extends PageRequest {

    // 操作类型（LOGIN, LOGOUT, USER_DISABLE 等）
    private String operation;

    // 操作模块
    private String module;

    // 操作结果：0-失败，1-成功
    private Integer result;

    // 用户名（模糊查询）
    private String username;
}
