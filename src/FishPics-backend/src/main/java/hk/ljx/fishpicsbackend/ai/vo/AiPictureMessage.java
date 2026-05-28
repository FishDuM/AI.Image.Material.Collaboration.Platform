package hk.ljx.fishpicsbackend.ai.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiPictureMessage {
    private String pictureName;

    private String introduction;

    private List<String> tags;
}
