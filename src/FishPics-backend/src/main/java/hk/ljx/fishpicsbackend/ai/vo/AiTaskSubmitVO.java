package hk.ljx.fishpicsbackend.ai.vo;

import lombok.Data;

@Data
public class AiTaskSubmitVO {

    private String taskId;
    private String status;

    public static AiTaskSubmitVO of(String taskId) {
        AiTaskSubmitVO vo = new AiTaskSubmitVO();
        vo.setTaskId(taskId);
        vo.setStatus("PENDING");
        return vo;
    }
}
