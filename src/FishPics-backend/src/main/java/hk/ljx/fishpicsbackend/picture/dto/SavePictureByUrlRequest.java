package hk.ljx.fishpicsbackend.picture.dto;

import lombok.Data;

@Data
public class SavePictureByUrlRequest {

    private String url;

    private Long targetSpaceId;
}
