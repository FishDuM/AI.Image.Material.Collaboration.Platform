package hk.ljx.fishpicsbackend.picture.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteByIdListRequest implements Serializable {
    @Size(max = 100, message = "单次最多删除100条")
    private List<Long> ids;
}
