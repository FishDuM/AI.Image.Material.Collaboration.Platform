package hk.ljx.fishpicsbackend.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统审计日志实体
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    // 操作类型（如：LOGIN, LOGOUT, USER_DISABLE, ROLE_CHANGE等）
    private String operation;

    private String module;

    private String detail;

    private String method;

    private String url;

    private String params;

    // 操作结果（0=失败，1=成功）
    private Integer result;

    private String errorMsg;

    private String ip;

    private LocalDateTime createTime;

    @TableLogic
    private Integer isDelete = 0;

    private static final long serialVersionUID = 1L;
}
