package hk.ljx.fishpicsbackend.ai.temp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiTagsIndexPicture {
    private Integer index;
    private List<String> tags;
}
