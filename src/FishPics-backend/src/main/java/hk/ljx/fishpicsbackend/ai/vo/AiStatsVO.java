package hk.ljx.fishpicsbackend.ai.vo;

import lombok.Data;

import java.util.Map;

@Data
public class AiStatsVO {

    private Long totalTasks;
    private Long successTasks;
    private Long failedTasks;
    private Long processingTasks;
    // key=类型编号, value=任务数
    private Map<String, Long> typeCounts;
}
