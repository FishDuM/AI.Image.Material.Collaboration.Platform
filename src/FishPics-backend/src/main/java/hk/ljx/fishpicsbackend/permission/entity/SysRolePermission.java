package hk.ljx.fishpicsbackend.permission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色-权限关联表
 */
@TableName("sys_role_permission")
@Data
public class SysRolePermission implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 角色ID
    private Long roleId;

    // 权限ID
    private Long permissionId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
