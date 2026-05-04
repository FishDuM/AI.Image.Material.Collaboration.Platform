package hk.ljx.fishpicsbackend.dto.picture;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteByIdList implements Serializable {
    private List<Long> ids;
}
