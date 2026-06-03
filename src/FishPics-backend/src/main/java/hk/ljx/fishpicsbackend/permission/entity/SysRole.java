package hk.ljx.fishpicsbackend.permission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 角色表
 */
@TableName("sys_role")
@Data
public class SysRole implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 角色编码，如 super_admin, team_admin
    private String code;

    // 角色显示名称
    private String name;

    // 0=系统级 1=团队级
    private Integer scope;

    // 系统预置角色不可删除 0=否 1=是
    private Integer isSystem;

    // 继承的角色ID（角色权限合并）
    private Long inheritRoleId;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
