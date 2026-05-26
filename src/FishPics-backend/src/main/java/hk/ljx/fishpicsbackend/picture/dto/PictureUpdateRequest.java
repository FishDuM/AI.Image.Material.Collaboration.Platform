package hk.ljx.fishpicsbackend.picture.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureUpdateRequest implements Serializable {
    private Long ids;
    private String pictureName;
    private String introduction;
    private String pictureUrl;
}
