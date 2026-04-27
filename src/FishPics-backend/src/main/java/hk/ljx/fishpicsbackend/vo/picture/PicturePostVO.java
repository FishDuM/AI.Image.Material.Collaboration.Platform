package hk.ljx.fishpicsbackend.vo.picture;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PicturePostVO {

    private String url;

    private Long pictureId;
}
