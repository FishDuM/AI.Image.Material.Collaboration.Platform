package hk.ljx.fishpicsbackend.permission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户-系统角色关联表
 */
@TableName("sys_user_role")
@Data
public class SysUserRole implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 用户ID
    private Long userId;

    // 角色ID
    private Long roleId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
