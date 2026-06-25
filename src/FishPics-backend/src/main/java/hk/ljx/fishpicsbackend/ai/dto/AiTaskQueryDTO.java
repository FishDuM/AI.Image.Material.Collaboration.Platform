package hk.ljx.fishpicsbackend.ai.dto;

import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiTaskQueryDTO extends PageRequest {

    private Integer type;
    private Integer status;
}
