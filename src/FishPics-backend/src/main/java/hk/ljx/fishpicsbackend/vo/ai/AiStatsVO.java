package hk.ljx.fishpicsbackend.vo.ai;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStatsVO {
    private Long totalTasks;
    private Long successTasks;
    private Long failedTasks;
    private Long processingTasks;
    private Map<Integer, Long> typeCounts;
}
