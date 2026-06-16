package hk.ljx.fishpicsbackend.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteTypeRequest {
    @NotBlank(message = "类型值不能为空")
    private String value;
}
