package hk.ljx.fishpicsbackend.picture.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PictureUpdateRequest implements Serializable {
    private Long id;
    @Size(max = 255, message = "图片名称最多255字符")
    private String pictureName;
    @Size(max = 2000, message = "图片介绍最多2000字符")
    private String introduction;
    private List<String> tags;
    /** 协同编辑覆盖：新图片 URL */
    private String url;
}
