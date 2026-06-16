package hk.ljx.fishpicsbackend.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteMarqueeRequest {
    @NotBlank(message = "URL不能为空")
    private String url;
}
