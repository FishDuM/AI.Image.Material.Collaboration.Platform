package hk.ljx.fishpicsbackend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiDrawPictureDTO {
    private String description;

    private String exclusion;

    private String style;

    private String size;
}
