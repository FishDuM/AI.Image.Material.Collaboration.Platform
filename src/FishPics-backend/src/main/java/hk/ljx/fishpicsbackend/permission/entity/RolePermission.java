package hk.ljx.fishpicsbackend.permission.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色-权限绑定表
 */
@TableName("role_permission")
@Data
public class RolePermission implements Serializable {

    /**
     * 关联 role.id
     */
    private Integer roleId;

    /**
     * 关联 permission.id
     */
    private Integer permId;

    private static final long serialVersionUID = 1L;
}
