package hk.ljx.fishpicsbackend.picture.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShareCancelRequest {

    @NotNull(message = "shareId不能为空")
    private Long shareId;
}
