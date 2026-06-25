package hk.ljx.fishpicsbackend.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiTaskVO {

    private Long id;
    private Long userId;
    private Integer type;
    private String subType;
    private Integer status;
    private Long pictureId;
    private LocalDateTime createTime;
    private String errorMsg;
}
