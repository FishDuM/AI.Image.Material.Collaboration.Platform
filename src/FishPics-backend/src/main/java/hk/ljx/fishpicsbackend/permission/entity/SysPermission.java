package hk.ljx.fishpicsbackend.permission.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 权限点表
 */
@TableName("sys_permission")
@Data
public class SysPermission implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 权限码，如 post:review
    private String code;

    // 权限名称
    private String name;

    // 所属模块：post/user/space/comment/picture/ai/system
    private String module;

    // 0=系统级 1=团队级 2=资源级
    private Integer scope;

    private Integer sortOrder;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
