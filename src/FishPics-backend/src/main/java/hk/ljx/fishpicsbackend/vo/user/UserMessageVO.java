package hk.ljx.fishpicsbackend.vo.user;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserMessageVO {

    private Long id;

    /**
     * 账号（登录用）
     */
    private String username;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 昵称（展示用）
     */
    private String nickname;

    /**
     * 用户的权限
     */
    private String role;

    /**
     * 创建时间
     */
    private Date createTime;

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
