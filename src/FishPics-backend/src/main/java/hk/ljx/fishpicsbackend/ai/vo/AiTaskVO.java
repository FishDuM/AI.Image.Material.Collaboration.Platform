package hk.ljx.fishpicsbackend.ai.vo;

import lombok.Data;

import java.util.Date;

@Data
public class AiTaskVO {

    private Long id;
    private Long userId;
    // 0=自动标注, 2=图片生成
    private Integer type;
    private String subType;
    // 0=处理中, 1=成功, 2=失败
    private Integer status;
    private Long pictureId;
    private Date createTime;
    private String errorMsg;
}
