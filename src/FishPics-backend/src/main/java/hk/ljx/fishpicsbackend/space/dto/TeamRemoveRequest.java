package hk.ljx.fishpicsbackend.space.dto;

import lombok.Data;

@Data
public class TeamRemoveRequest {
    private Long spaceId;
    private Long userId;
}
