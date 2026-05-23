package hk.ljx.fishpicsbackend.vo.ai;

import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTaskVO {
    private Long id;
    private Integer type;
    private String subType;
    private Integer status;
    private String errorMsg;
    private Long pictureId;
    private Date createTime;
    private Object output;
}
