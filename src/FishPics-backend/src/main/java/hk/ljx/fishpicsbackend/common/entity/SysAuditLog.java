package hk.ljx.fishpicsbackend.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统审计日志实体
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户ID
    private Long userId;

    // 用户名
    private String username;

    // 操作类型（如：LOGIN, LOGOUT, USER_DISABLE, ROLE_CHANGE等）
    private String operation;

    // 操作模块
    private String module;

    // 操作详情
    private String detail;

    // 请求方法（GET, POST等）
    private String method;

    // 请求URL
    private String url;

    // 请求参数
    private String params;

    // 操作结果（0=失败，1=成功）
    private Integer result;

    // 错误信息
    private String errorMsg;

    // IP地址
    private String ip;

    // 操作时间
    private LocalDateTime createTime;

    @TableLogic
    private Integer isDelete = 0;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
