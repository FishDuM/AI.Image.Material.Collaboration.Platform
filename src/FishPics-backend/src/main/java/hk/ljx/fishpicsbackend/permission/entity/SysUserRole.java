package hk.ljx.fishpicsbackend.permission.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户-系统角色关联表
 * 只有超管需要登记（role_id = 1）
 */
@TableName("sys_user_role")
@Data
public class SysUserRole implements Serializable {

    /**
     * 关联 user.id
     */
    private Long userId;

    /**
     * 关联 role.id（仅允许填 1 = 超管）
     */
    private Integer roleId;

    private static final long serialVersionUID = 1L;
}
