package hk.ljx.fishpicsbackend.vo.user;

import hk.ljx.fishpicsbackend.vo.post.PostListVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserMessageVO {

    private Long id;

    /**
     * 用户名（登录用）
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
     * 创建时间
     */
    private Date createTime;

    /**
     * 帖子列表
     */
    private List<PostListVO> postList;
}
