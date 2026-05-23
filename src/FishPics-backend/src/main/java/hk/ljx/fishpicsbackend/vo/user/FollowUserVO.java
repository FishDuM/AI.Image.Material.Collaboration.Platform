package hk.ljx.fishpicsbackend.vo.user;

import lombok.Data;

@Data
public class FollowUserVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private Integer level;
}
