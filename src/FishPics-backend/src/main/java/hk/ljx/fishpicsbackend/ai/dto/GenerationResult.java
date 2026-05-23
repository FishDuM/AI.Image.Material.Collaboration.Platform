package hk.ljx.fishpicsbackend.ai.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationResult {
    private String taskId;
    private Integer status;
    private List<String> resultUrls;
}
