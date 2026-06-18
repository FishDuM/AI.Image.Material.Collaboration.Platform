package hk.ljx.fishpicsbackend.picture.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadChunkVO {

    private String etag;

    private Integer chunkIndex;
}
