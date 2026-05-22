package hk.ljx.fishpicsbackend.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImageCompressRequest implements Serializable {

    private Long pictureId;

    private Double quality;

    private Long targetSizeBytes;

    private String format;
}