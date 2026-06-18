package hk.ljx.fishpicsbackend.picture.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareInfoVO {

    private LocalDateTime expireTime;

    private Integer allowDownload;

    private List<SharePictureVO> pictures;
}
