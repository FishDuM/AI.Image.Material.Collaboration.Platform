package hk.ljx.fishpicsbackend.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiDrawPictureDTO {
    @NotBlank(message = "描述不能为空")
    @Size(max = 500, message = "描述最多500字")
    private String description;

    @Size(max = 200, message = "排除词最多200字")
    private String exclusion;

    private String style;

    private String size;
}
