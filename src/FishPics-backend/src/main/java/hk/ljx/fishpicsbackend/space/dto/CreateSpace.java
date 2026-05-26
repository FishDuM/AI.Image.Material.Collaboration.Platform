package hk.ljx.fishpicsbackend.space.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建空间请求DTO
 * 私人空间(type=0)每人限1个，团队空间(type=1)按等级限制数量
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateSpace {

    /** 空间名称，不能为空，最大20字符 */
    private String name;

    /** 空间介绍，最大200字符 */
    private String introduction;

    /**
     * 空间类型：0-私人空间（每人限1个），1-团队空间（按等级限数量）
     */
    private Integer type;
}
