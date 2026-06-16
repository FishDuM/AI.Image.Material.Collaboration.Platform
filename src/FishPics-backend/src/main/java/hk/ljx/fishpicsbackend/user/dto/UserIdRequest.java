package hk.ljx.fishpicsbackend.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserIdRequest {
    @NotNull(message = "用户ID不能为空")
    private Long userId;
}
