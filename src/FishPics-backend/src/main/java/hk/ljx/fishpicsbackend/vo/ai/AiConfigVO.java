package hk.ljx.fishpicsbackend.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConfigVO {
    private Boolean taggingEnabled;
    private Boolean editingEnabled;
    private Boolean generationEnabled;
    private Boolean recommendationEnabled;
}
