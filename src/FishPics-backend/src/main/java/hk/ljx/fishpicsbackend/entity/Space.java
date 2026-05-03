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

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}