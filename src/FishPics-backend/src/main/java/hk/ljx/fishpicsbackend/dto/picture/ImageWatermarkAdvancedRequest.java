package hk.ljx.fishpicsbackend.dto.picture;

import lombok.Data;

import java.io.Serializable;

@Data
public class ImageWatermarkAdvancedRequest implements Serializable {

    private Long pictureId;

    private String text;

    private String imageUrl;

    private String position = "center";

    private Float opacity = 0.5f;

    private Integer fontSize = 36;

    private String format;
}