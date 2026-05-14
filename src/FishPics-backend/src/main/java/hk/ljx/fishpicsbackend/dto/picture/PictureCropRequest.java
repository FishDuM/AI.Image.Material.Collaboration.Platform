package hk.ljx.fishpicsbackend.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureCropRequest implements Serializable {

    /**
     * 图片id
     */
    private Long pictureId;

    /**
     * 裁剪区域左上角x坐标（原始图像像素坐标系）
     */
    private Double x;

    /**
     * 裁剪区域左上角y坐标（原始图像像素坐标系）
     */
    private Double y;

    /**
     * 裁剪区域宽度（原始图像像素坐标系）
     */
    private Double width;

    /**
     * 裁剪区域高度（原始图像像素坐标系）
     */
    private Double height;

    /**
     * 旋转角度，仅支持90的倍数（0/90/180/270），0表示不旋转
     */
    private Integer rotation;

    /**
     * 设备类型（可选，保留字段）
     */
    private String deviceType;

    /**
     * 输出格式（png/jpg），为null时默认png
     */
    private String format;
}
