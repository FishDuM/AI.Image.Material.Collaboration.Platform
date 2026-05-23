package hk.ljx.fishpicsbackend.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationRequest {
    private String prompt;
    private String negativePrompt;
    private Integer width;
    private Integer height;
    private Integer numImages;
}
