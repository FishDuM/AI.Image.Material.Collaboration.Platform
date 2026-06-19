package hk.ljx.fishpicsbackend.common.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IdRequest {

    @NotNull(message = "ID不能为空")
    private Long id;
}
