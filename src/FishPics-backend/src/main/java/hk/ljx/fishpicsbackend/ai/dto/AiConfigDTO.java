package hk.ljx.fishpicsbackend.ai.dto;

import lombok.Data;

@Data
public class AiConfigDTO {

    /**
     * 自动标注开关
     */
    private Boolean taggingEnabled;

    /**
     * 图片编辑开关
     */
    private Boolean editingEnabled;

    /**
     * 图片生成开关
     */
    private Boolean generationEnabled;

    /**
     * 智能推荐开关
     */
    private Boolean recommendationEnabled;
}
