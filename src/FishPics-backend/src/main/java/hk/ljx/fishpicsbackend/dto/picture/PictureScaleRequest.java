package hk.ljx.fishpicsbackend.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureScaleRequest implements Serializable {

    /**
     * 图片id
     */
    private Long pictureId;

    /**
     * 缩放比例，范围(0, 10]，如0.5表示缩小一半，2表示放大两倍
     */
    private Double scale;

    /**
     * 目标宽度（像素），与scale互斥，传入后自动计算等比缩放比例
     */
    private Integer targetWidth;

    /**
     * 目标高度（像素），可选，保留字段
     */
    private Integer targetHeight;

    /**
     * 设备类型（可选，保留字段）
     */
    private String deviceType;

    /**
     * 输出格式（png/jpg），为null时默认png
     */
    private String format;
}
