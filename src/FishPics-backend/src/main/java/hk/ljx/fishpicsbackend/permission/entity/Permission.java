package hk.ljx.fishpicsbackend.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 权限表
 */
@TableName("permission")
@Data
public class Permission implements Serializable {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 权限标识，如 system:config
     */
    private String permKey;

    /**
     * 权限名称
     */
    private String permName;

    /**
     * 所属层级：system / space / resource
     */
    private String layer;

    private static final long serialVersionUID = 1L;
}
