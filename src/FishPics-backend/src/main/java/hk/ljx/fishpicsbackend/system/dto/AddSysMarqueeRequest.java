package hk.ljx.fishpicsbackend.system.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddSysMarqueeRequest implements Serializable {
    @NotEmpty(message = "图片ID列表不能为空")
    private List<String> pictureIds;
}
