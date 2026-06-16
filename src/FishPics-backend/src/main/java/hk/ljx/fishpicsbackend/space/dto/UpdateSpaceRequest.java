package hk.ljx.fishpicsbackend.space.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新空间信息请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSpaceRequest {

    // 空间ID，必填
    @NotNull(message = "空间ID不能为空")
    private Long id;

    // 新空间名称，必填
    @NotBlank(message = "空间名称不能为空")
    private String name;

    // 新空间介绍
    private String introduction;
}
