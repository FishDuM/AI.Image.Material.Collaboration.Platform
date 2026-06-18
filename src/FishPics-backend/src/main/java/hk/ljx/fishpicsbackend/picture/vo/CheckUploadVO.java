package hk.ljx.fishpicsbackend.picture.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CheckUploadVO {

    private String status;

    private PictureVO picture;

    private List<Integer> uploadedChunks;

    private String uploadId;

    private String cosKey;
}
