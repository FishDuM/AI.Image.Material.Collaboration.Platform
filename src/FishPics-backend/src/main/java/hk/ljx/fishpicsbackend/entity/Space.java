package hk.ljx.fishpicsbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

import lombok.Data;

/**
 * 空间表
 * @TableName space
 */
@TableName(value ="space")
@Data
public class Space implements Serializable {
    /**
     * 空间id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 空间介绍
     */
    private String introduction;

    /**
     * 0-私人空间，1-团队空间
     */
    private Integer type;

    /**
     * 团队空间的用户id
     */
    private String teamUsersId;

    /**
     * 创建的用户Id
     */
    private Long userId;

    /**
     * 空间的存储大小(KB)：512MB-5G-10G
     */
    private Long storageSize;

    /**
     * 空间级别：普通-VIP-SVIP
     */
    private Integer level;

    /**
     * 空间名
     */
    private String name;

    /**
     * 现在使用大小
     */
    private Long size;

    /**
     * 0=禁用, 1=正常
     */
    private Integer status;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}