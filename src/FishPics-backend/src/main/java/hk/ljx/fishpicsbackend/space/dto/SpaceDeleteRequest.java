package hk.ljx.fishpicsbackend.space.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SpaceDeleteRequest {
    @NotNull(message = "空间ID不能为空")
    private Long id;
}
