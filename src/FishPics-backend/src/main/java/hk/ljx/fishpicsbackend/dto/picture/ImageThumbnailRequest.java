package hk.ljx.fishpicsbackend.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImageThumbnailRequest implements Serializable {

    private Long pictureId;

    private Integer width;

    private Integer height;

    private String format;

    private boolean keepAspectRatio = true;
}