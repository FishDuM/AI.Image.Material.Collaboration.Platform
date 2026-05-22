package hk.ljx.fishpicsbackend.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImageRotateRequest implements Serializable {

    private Long pictureId;

    private Double angle;

    private String format;
}