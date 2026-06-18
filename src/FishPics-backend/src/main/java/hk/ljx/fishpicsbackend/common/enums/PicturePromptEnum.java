package hk.ljx.fishpicsbackend.common.enums;

import lombok.Getter;

@Getter
public enum PicturePromptEnum {

    PHOTOGRAPHY("photography",
            " Photorealistic photography style, natural light, cinematic composition, high detail."),
    ANIME("anime",
            " Anime illustration style, clean line art, vivid colors, expressive composition."),
    OIL_PAINTING("oil painting",
            " Oil painting style, visible brush strokes, rich texture, classical artistic lighting."),
    WATERCOLOR("watercolor",
            " Watercolor style, soft edges, transparent pigments, poetic and fresh atmosphere."),
    SKETCH("sketch",
            " Pencil sketch style, monochrome shading, hand-drawn lines, strong structure."),
    THREE_D("3d",
            " 3D rendered style, polished materials, soft lighting, depth and volume."),
    PIXEL_ART("pixel art",
            " Pixel art style, crisp blocky forms, retro game aesthetics, limited palette."),
    FLAT_ILLUSTRATION("flat illustration",
            " Flat illustration style, clean vector shapes, modern colors, minimal shadows."),
    CHINESE_PAINTING("chinese painting",
            " Traditional Chinese ink painting style, expressive brushwork, elegant blank space."),
    CYBERPUNK("cyberpunk",
            " Cyberpunk style, neon lights, futuristic city, high contrast, rain-soaked atmosphere.");

    private final String code;
    private final String prompt;

    PicturePromptEnum(String code, String prompt) {
        this.code = code;
        this.prompt = prompt;
    }

    public static String getPromptByCode(String code) {
        for (PicturePromptEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value.getPrompt();
            }
        }
        return "";
    }

    public static boolean isValidCode(String code) {
        for (PicturePromptEnum value : values()) {
            if (value.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
