package hk.ljx.fishpicsbackend.ai.dto;

import lombok.Data;

@Data
public class AiTaskQueryDTO {

    /**
     * 当前页码，默认1
     */
    private Integer current = 1;

    /**
     * 每页条数，默认20
     */
    private Integer pageSize = 20;

    /**
     * 任务类型: 0=自动标注, 2=图片生成，不传则查全部
     */
    private Integer type;

    /**
     * 任务状态: 0=处理中, 1=成功, 2=失败，不传则查全部
     */
    private Integer status;
}
