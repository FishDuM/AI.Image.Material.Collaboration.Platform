package hk.ljx.fishpicsbackend.ai.vo;

import lombok.Data;

import java.util.Map;

/**
 * AI 使用统计
 */
@Data
public class AiStatsVO {

    /**
     * 总任务数
     */
    private Long totalTasks;

    /**
     * 成功任务数
     */
    private Long successTasks;

    /**
     * 失败任务数
     */
    private Long failedTasks;

    /**
     * 处理中任务数
     */
    private Long processingTasks;

    /**
     * 按类型统计: key=类型编号, value=任务数
     */
    private Map<String, Long> typeCounts;
}
