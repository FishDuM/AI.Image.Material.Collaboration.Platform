package hk.ljx.fishpicsbackend.space.dto;

import lombok.Data;

@Data
public class TeamInviteRequest {
    private Long spaceId;
    private Long userId;
    private Long roleId;
}
