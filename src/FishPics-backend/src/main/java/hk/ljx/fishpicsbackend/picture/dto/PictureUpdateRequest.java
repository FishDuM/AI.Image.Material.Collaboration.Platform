package hk.ljx.fishpicsbackend.picture.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class PictureUpdateRequest implements Serializable {
    private Long id;
    private String pictureName;
    private String introduction;
    private List<String> tags;
}
