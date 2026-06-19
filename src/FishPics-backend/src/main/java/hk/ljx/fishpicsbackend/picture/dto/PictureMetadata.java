package hk.ljx.fishpicsbackend.picture.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureMetadata {

    private String pictureName;

    private String width;

    private String height;

    /** 大小（字节） */
    private Long size;

    private String url;
}
