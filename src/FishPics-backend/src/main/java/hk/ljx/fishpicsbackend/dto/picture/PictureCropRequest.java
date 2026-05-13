package hk.ljx.fishpicsbackend.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class PictureCropRequest implements Serializable {
    private Long pictureId;
    private Double x;
    private Double y;
    private Double width;
    private Double height;
    private Integer rotation;
}