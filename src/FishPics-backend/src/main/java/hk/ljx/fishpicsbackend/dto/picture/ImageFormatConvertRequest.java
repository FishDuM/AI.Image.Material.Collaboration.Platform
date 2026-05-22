package hk.ljx.fishpicsbackend.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImageFormatConvertRequest implements Serializable {

    private Long pictureId;

    private String targetFormat;

    private Double quality;
}