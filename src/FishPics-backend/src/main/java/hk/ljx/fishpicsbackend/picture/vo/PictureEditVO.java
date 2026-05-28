package hk.ljx.fishpicsbackend.picture.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PictureEditVO {

    /**
     * 图片名称
     */
    private String pictureName;

    /**
     * 图片地址
     */
    private String url;

    /**
     * 图片介绍
     */
    private String introduction;

    /**
     * 图片标签 (逗号分隔)
     */
    private List<String> tags;
}
