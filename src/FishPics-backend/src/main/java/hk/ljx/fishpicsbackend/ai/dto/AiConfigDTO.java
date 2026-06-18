package hk.ljx.fishpicsbackend.ai.dto;

import lombok.Data;

@Data
public class AiConfigDTO {

    private Boolean taggingEnabled;
    private Boolean editingEnabled;
    private Boolean generationEnabled;
    private Boolean recommendationEnabled;

    /** VIP 月度标注配额 */
    private Integer vipTagQuota;
    /** VIP 月度生图配额 */
    private Integer vipDrawQuota;
    /** SVIP 月度标注配额 */
    private Integer svipTagQuota;
    /** SVIP 月度生图配额 */
    private Integer svipDrawQuota;

    /** 全局默认配额常量（唯一来源） */
    public static final int DEFAULT_VIP_TAG_QUOTA = 1000;
    public static final int DEFAULT_VIP_DRAW_QUOTA = 50;
    public static final int DEFAULT_SVIP_TAG_QUOTA = 5000;
    public static final int DEFAULT_SVIP_DRAW_QUOTA = 200;

    /** 创建带默认值的配置 */
    public static AiConfigDTO withDefaults() {
        AiConfigDTO config = new AiConfigDTO();
        config.setTaggingEnabled(true);
        config.setEditingEnabled(false);
        config.setGenerationEnabled(true);
        config.setRecommendationEnabled(true);
        config.setVipTagQuota(DEFAULT_VIP_TAG_QUOTA);
        config.setVipDrawQuota(DEFAULT_VIP_DRAW_QUOTA);
        config.setSvipTagQuota(DEFAULT_SVIP_TAG_QUOTA);
        config.setSvipDrawQuota(DEFAULT_SVIP_DRAW_QUOTA);
        return config;
    }

    /** 将 null 配额字段填充为默认值 */
    public void fillDefaults() {
        if (vipTagQuota == null) vipTagQuota = DEFAULT_VIP_TAG_QUOTA;
        if (vipDrawQuota == null) vipDrawQuota = DEFAULT_VIP_DRAW_QUOTA;
        if (svipTagQuota == null) svipTagQuota = DEFAULT_SVIP_TAG_QUOTA;
        if (svipDrawQuota == null) svipDrawQuota = DEFAULT_SVIP_DRAW_QUOTA;
    }
}
