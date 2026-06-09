package hk.ljx.fishpicsbackend.picture.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PicturePageVO {

    private List<PictureVO> records;

    private long total;
}
