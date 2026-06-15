package hk.ljx.fishpicsbackend.space.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

    // 空间ID，必填
    private Long id;

    // 空间名称
    private String name;

    // 空间介绍
    private String introduction;

    // 空间级别：0-普通, 1-VIP, 2-SVIP
    private Integer level;

    /**
     * 存储空间大小（字节），上限 1TB
     */
    @Min(value = 0, message = "存储空间大小不能为负数")
    @Max(value = 1024L * 1024 * 1024 * 1024, message = "存储空间大小不能超过 1TB")
    private Long storageSize;
}
