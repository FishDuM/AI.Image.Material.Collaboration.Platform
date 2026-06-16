package hk.ljx.fishpicsbackend.ai.dto;

import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiTaskQueryDTO extends PageRequest {

    // 0=自动标注, 2=图片生成，不传查全部
    private Integer type;

    // 0=处理中, 1=成功, 2=失败，不传查全部
    private Integer status;
}
