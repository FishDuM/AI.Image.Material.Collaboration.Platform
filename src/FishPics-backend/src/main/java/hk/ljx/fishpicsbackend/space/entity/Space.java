package hk.ljx.fishpicsbackend.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
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

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    private static final long serialVersionUID = 1L;

    /**
     * 校验空间存在且状态为启用
     */
    public static void validateActive(Space space) {
        ExcUtils.throwIfTrue(space == null, ExceptionCode.PARAMETER_ERROR, "空间不存在");
        ExcUtils.throwIfTrue(!Integer.valueOf(1).equals(space.getStatus()), ExceptionCode.FORBIDDEN, "空间已被禁用");
    }
}