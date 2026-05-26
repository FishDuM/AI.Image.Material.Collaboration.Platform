package hk.ljx.fishpicsbackend.user.vo;

import lombok.Data;

@Data
public class FollowUserVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private Integer level;
}
