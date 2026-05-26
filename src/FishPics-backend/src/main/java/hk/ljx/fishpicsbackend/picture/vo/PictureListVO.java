package hk.ljx.fishpicsbackend.picture.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureListVO {

    private Long id;

    private String url;

    private List<String> tags;
}
