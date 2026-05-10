package hk.ljx.fishpicsbackend.dto.space;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import hk.ljx.fishpicsbackend.dto.base.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 空间查询条件包装器（继承PageRequest，支持分页排序）
 * 非空字段才会加入查询条件（等值匹配）
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpaceQueryWrapper extends PageRequest implements Serializable {

    /** 空间ID */
    private Long id;

    /** 空间介绍 */
    private String introduction;

    /** 空间类型：0-私人空间，1-团队空间 */
    private Integer type;

    /** 团队空间成员用户ID（私人空间不使用） */
    private String teamUsersId;

    /** 创建者用户ID */
    private Long userId;

    /** 空间存储大小(字节)：私人空间512MB/5GB/10GB，团队空间512MB/30GB/50GB */
    private Long storageSize;

    /** 空间等级：0-普通，1-VIP，2-SVIP */
    private Integer level;

    /** 空间名称 */
    private String name;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
