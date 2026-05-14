package hk.ljx.fishpicsbackend.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureWatermarkRequest implements Serializable {

    /**
     * 图片id
     */
    private Long pictureId;

    /**
     * 水印文字内容，支持中英文
     */
    private String text;

    /**
     * 设备类型（可选，保留字段）
     */
    private String deviceType;

    /**
     * 输出格式（png/jpg），为null时默认png
     */
    private String format;
}
