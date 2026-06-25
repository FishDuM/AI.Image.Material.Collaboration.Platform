package hk.ljx.fishpicsbackend.ai.vo;

import lombok.Data;

import java.util.Map;

@Data
public class AiStatsVO {

    private Long totalTasks;
    private Long successTasks;
    private Long failedTasks;
    private Long processingTasks;
    private Map<String, Long> typeCounts;
}
