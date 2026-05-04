package hk.ljx.fishpicsbackend.dto.space;

import com.baomidou.mybatisplus.annotation.TableField;
import hk.ljx.fishpicsbackend.dto.base.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpaceQueryWrapper extends PageRequest implements Serializable {

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

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
