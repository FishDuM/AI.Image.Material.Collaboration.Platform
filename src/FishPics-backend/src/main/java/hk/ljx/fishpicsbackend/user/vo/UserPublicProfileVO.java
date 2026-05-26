package hk.ljx.fishpicsbackend.user.vo;

import lombok.Data;
import java.util.Date;

@Data
public class UserPublicProfileVO {
    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private Integer level;
    private Date createTime;
    private Boolean isFollowed;

    private Long postCount;
    private Long collectCount;
    private Long likeCount;
    private Long followsCount;
    private Long fansCount;
}
