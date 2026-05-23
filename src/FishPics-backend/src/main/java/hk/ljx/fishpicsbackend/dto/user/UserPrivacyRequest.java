package hk.ljx.fishpicsbackend.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPrivacyRequest {

    /**
     * 关注列表可见性 (0=公开, 1=私密)
     */
    private Integer isPrivateFollows;

    /**
     * 收藏列表可见性 (0=公开, 1=私密)
     */
    private Integer isPrivatePostCollect;

    /**
     * 点赞列表可见性 (0=公开, 1=私密)
     */
    private Integer isPrivateLikes;

    /**
     * 粉丝列表可见性 (0=公开, 1=私密)
     */
    private Integer isPrivateFans;
}
