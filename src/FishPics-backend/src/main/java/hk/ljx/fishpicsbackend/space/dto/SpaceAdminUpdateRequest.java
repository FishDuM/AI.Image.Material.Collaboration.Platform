package hk.ljx.fishpicsbackend.space.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员编辑空间信息请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpaceAdminUpdateRequest {

    /** 空间ID，必填 */
    private Long id;

    /** 空间名称 */
    private String name;

    /** 空间介绍 */
    private String introduction;

    /** 存储空间大小(Byte) */
    private Long storageSize;

    /** 空间级别：0-普通, 1-VIP, 2-SVIP */
    private Integer level;
}
