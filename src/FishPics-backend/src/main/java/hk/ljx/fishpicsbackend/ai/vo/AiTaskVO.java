package hk.ljx.fishpicsbackend.ai.vo;

import lombok.Data;

import java.util.Date;

/**
 * AI 任务管理后台视图对象
 */
@Data
public class AiTaskVO {

    private Long id;

    private Long userId;

    /**
     * 任务类型: 0=自动标注, 2=图片生成
     */
    private Integer type;

    /**
     * 子类型
     */
    private String subType;

    /**
     * 状态: 0=处理中, 1=成功, 2=失败
     */
    private Integer status;

    /**
     * 业务关联ID（图片ID等）
     */
    private String pictureId;

    private Date createTime;

    private String errorMsg;
}
