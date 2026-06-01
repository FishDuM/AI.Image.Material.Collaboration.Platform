package hk.ljx.fishpicsbackend.common.enums;

import lombok.Getter;

/**
 * 绘图尺寸枚举
 */
@Getter
public enum PictureSizeEnum {

    SQUARE("1:1", 2048, 2048),
    WIDE("16:9", 2688, 1536),
    TALL("9:16", 1536, 2688),
    STANDARD("4:3", 2368, 1728),
    PORTRAIT("3:4", 1728, 2368);

    private final String code;
    private final Integer width;
    private final Integer height;

    PictureSizeEnum(String code, Integer width, Integer height) {
        this.code = code;
        this.width = width;
        this.height = height;
    }

    /**
     * 根据 code 获取尺寸枚举
     */
    public static PictureSizeEnum getByCode(String code) {
        for (PictureSizeEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return SQUARE;
    }

    /**
     * 根据 code 获取 "宽*高" 格式的尺寸字符串
     */
    public static String getSizeByCode(String code) {
        PictureSizeEnum size = getByCode(code);
        return size.getWidth() + "*" + size.getHeight();
    }
}
