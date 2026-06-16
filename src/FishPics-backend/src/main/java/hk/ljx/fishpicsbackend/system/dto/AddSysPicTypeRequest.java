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
public class AddSysPicTypeRequest implements Serializable {
    @NotEmpty(message = "类型列表不能为空")
    private List<String> value;
}
