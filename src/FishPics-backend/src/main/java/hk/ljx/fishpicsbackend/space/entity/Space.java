package hk.ljx.fishpicsbackend.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

import static hk.ljx.fishpicsbackend.common.constants.SpaceConstants.SPACE_STATUS_ENABLED;

/**
 * 空间表
 * @TableName space
 */
@TableName(value ="space")
@Data
public class Space implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String introduction;

    private Integer type;

    private Long userId;

    private Long storageSize;

    private Integer level;

    private String name;

    private Long size;

    private Integer status;

    @Version
    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;

    /**
     * 校验空间存在且状态为启用
     */
    public static void validateActive(Space space) {
        ExcUtils.throwIfTrue(space == null, ExceptionCode.PARAMETER_ERROR, "空间不存在");
        ExcUtils.throwIfTrue(!ExcUtils.eq(space.getStatus(), SPACE_STATUS_ENABLED),
                ExceptionCode.FORBIDDEN, "空间已被禁用");
    }
}
