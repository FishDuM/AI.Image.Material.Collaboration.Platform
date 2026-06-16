package hk.ljx.fishpicsbackend.space.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SpaceSetStatusRequest {
    @NotNull(message = "空间ID不能为空")
    private Long id;
    @NotNull(message = "状态不能为空")
    private Integer status;
}
