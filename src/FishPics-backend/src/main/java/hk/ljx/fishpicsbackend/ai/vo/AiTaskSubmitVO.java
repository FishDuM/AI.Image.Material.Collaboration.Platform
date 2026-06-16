package hk.ljx.fishpicsbackend.ai.vo;

import lombok.Data;

/**
 * AI 任务提交结果
 */
@Data
public class AiTaskSubmitVO {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 任务状态
     */
    private String status;

    public static AiTaskSubmitVO of(String taskId) {
        AiTaskSubmitVO vo = new AiTaskSubmitVO();
        vo.setTaskId(taskId);
        vo.setStatus("PENDING");
        return vo;
    }
}
