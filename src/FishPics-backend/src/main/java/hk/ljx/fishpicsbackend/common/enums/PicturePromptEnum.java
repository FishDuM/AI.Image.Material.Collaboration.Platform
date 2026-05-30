package hk.ljx.fishpicsbackend.common.enums;

import lombok.Getter;

/**
 * 绘图风格提示词枚举
 */
@Getter
public enum PicturePromptEnum {

    AUTO("auto", ""),

    PHOTOGRAPHY("photography",
            "写实摄影风格，极致画质，8K超高清，使用35mm定焦镜头拍摄，大光圈浅景深，背景自然虚化，柔和自然光或黄金时刻光线，画面具有电影感和叙事氛围，色彩真实饱满，纹理细节纤毫毕现，如同《国家地理》获奖摄影作品，构图考究。"),

    PORTRAIT("portrait",
            "精致的人像肖像，柔和棚拍灯光，使用85mm人像镜头，f/1.8大光圈，背景呈现梦幻的圆形光斑虚化，皮肤质感细腻真实，微妙的血管和毛孔细节可见，专业级后期修饰，人物神情自然传神，气质优雅高级，整体影调温暖柔和。"),

    THREE_D_CARTOON("3d cartoon",
            "3D卡通渲染风格，皮克斯与迪士尼动画电影质感，使用C4D和Octane渲染器制作，光线柔和通透，色彩鲜艳明亮，角色造型圆润可爱，表情生动夸张，毛发和材质细节高度精致，场景具有空间感和体积感，画面干净、富有童趣，8K分辨率。"),

    ANIME("anime",
            "日式动漫风格插画，融合新海诚的唯美光影与吉卜力的细腻手绘感，赛璐珞风格上色，线条干净利落，色彩通透明快，高光晕染柔和，场景细节丰富，可能带有飘落的花瓣、流动的云彩或闪烁的光斑，整体氛围清新治愈，高质量动画电影画质。"),

    OIL_PAINTING("oil painting",
            "古典油画风格，浓厚的厚涂笔触和明显的画布纹理，使用调色刀和鬃毛笔的肌理效果，色彩浓郁且富有层次，采用伦勃朗式布光，明暗对比强烈，颜料堆叠形成立体感，仿佛一幅经过岁月沉淀的博物馆级大师作品，具有古典艺术的厚重与深邃。"),

    WATERCOLOR("watercolor",
            "水彩画风格，湿画法自然晕染，色彩在湿润的纸面上自由扩散和混合，形成柔和的渐变和边缘，局部有细腻的水渍痕迹和颜料飞溅效果，冷压水彩纸的颗粒质感清晰可见，整体色调淡雅清新，通透轻盈，充满诗意和朦胧的美感。"),

    SKETCH("sketch",
            "铅笔素描风格，使用多种硬度的石墨铅笔绘制，细腻的排线与交叉阴影表现出丰富的灰阶层次，笔触轻重变化微妙，高光区域留白自然，纸纹质感真实，整体为单色或略带暖灰调，线条流畅而精准，具有扎实的学院派造型功底和强烈的体积感。"),

    CHINESE_PAINTING("chinese painting",
            "中国传统水墨画风格，采用写意或兼工带写的技法，在生宣纸上以墨色为主，墨分五色，通过浓淡干湿变化营造气韵，讲究留白与意境，构图疏朗空灵，可能伴有淡雅的赭石或花青点染，角落盖有朱红色印章，整体散发东方哲学的诗意与禅意。"),

    FLAT_ILLUSTRATION("flat illustration",
            "扁平化矢量插画风格，现代极简设计，使用大胆而和谐的高饱和配色，造型由清晰的几何形状和流畅的曲线构成，没有任何阴影和渐变，边缘锐利干脆，具有强烈的平面装饰感，构图富有节奏和秩序，画面明快活泼，适合现代商业和数字媒体。");

    private final String code;
    private final String prompt;

    PicturePromptEnum(String code, String prompt) {
        this.code = code;
        this.prompt = prompt;
    }

    /**
     * 根据 code 获取对应的提示词
     */
    public static String getPromptByCode(String code) {
        for (PicturePromptEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value.getPrompt();
            }
        }
        return "";
    }
}