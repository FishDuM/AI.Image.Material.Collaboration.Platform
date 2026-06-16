package hk.ljx.fishpicsbackend.ai.dto;

import lombok.Data;

@Data
public class AiConfigDTO {

    private Boolean taggingEnabled;
    private Boolean editingEnabled;
    private Boolean generationEnabled;
    private Boolean recommendationEnabled;
}
