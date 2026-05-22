package hk.ljx.fishpicsbackend.dto.picture;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageBatchResult implements Serializable {

    private int total;

    private int successCount;

    private int failCount;

    private List<ImageProcessItem> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageProcessItem implements Serializable {
        private Long pictureId;
        private String newUrl;
        private boolean success;
        private String message;
    }
}