package hk.ljx.fishpicsbackend.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class ImageBatchRequest implements Serializable {

    private List<Long> pictureIds;

    private Double quality;

    private String format;

    private Long targetSizeBytes;
}