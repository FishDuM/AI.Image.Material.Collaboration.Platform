package hk.ljx.fishpicsbackend.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 角色表
 */
@TableName("role")
@Data
public class Role implements Serializable {

    /**
     * 1=超管, 2=团队管理员, 3=普通成员, 4=只读
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色描述
     */
    private String description;

    private static final long serialVersionUID = 1L;
}
