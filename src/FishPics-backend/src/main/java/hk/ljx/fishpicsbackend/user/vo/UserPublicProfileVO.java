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
}
