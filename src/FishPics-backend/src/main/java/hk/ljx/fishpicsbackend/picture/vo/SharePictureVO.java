package hk.ljx.fishpicsbackend.picture.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SharePictureVO {

    private Long pictureId;

    private String pictureName;

    private String introduction;

    private String width;

    private String height;

    private String previewUrl;

    private String downloadUrl;
}
