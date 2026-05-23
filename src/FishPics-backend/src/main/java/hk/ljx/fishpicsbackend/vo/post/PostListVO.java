package hk.ljx.fishpicsbackend.vo.post;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostListVO {
    /**
     * 主键
     */
    private Long id;

    /**
     * 关联用户
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 点赞数
     */
    private Long likesNum;

    /**
     * 标题
     */
    private String title;

    /**
     * 封面图片的 url
     */
    private String url;

    /**
     * 收藏数
     */
    private Long collectsNum;

    /**
     * 当前用户是否已收藏
     */
    private Boolean isCollected;
}
