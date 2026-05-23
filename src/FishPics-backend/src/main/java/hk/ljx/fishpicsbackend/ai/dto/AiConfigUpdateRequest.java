package hk.ljx.fishpicsbackend.ai.dto;

import java.io.Serializable;
import lombok.Data;

@Data
public class AiConfigUpdateRequest implements Serializable {
    private Boolean taggingEnabled;
    private Boolean editingEnabled;
    private Boolean generationEnabled;
    private Boolean recommendationEnabled;
}
