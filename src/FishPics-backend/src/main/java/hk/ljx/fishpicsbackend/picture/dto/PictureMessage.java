package hk.ljx.fishpicsbackend.picture.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureMessage {

    /**
     * 图片名称
     */
    private String pictureName;

    /**
     * 宽度
     */
    private String width;

    /**
     * 高度
     */
    private String height;

    /**
     * 大小
     */
    private String size;

    /**
     * 图片 url 地址
     */
    private String url;
}
