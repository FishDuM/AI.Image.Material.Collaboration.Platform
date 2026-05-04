package hk.ljx.fishpicsbackend.vo.picture;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PicturePageVO {

    private List<PictureListVO> records;

    private long total;
}
