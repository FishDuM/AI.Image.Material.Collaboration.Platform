package hk.ljx.fishpicsbackend.space.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TeamInviteRequest {
    @NotNull(message = "空间ID不能为空")
    private Long spaceId;
    @NotNull(message = "用户ID不能为空")
    private Long userId;
    @NotNull(message = "角色ID不能为空")
    private Long roleId;
}
