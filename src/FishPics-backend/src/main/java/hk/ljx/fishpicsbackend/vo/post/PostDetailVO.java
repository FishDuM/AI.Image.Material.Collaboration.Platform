package hk.ljx.fishpicsbackend.vo.post;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostDetailVO {
    /**
     * 帖子 id
     */
    private Long id;

    /**
     * 关联用户表
     */
    private Long userId;

    /**
     * 用户名称
     */
    private String username;

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 点赞数
     */
    private Long likesNum;

    /**
     * 收藏数
     */
    private Long collectsNum;

    /**
     * 评论数
     */
    private Integer commentNum;

    /**
     * 图片 url 列表
     */
    private List<String> pictureUrl;

    /**
     * 图片 id 列表
     */
    private List<Long> pictureIds;

    /**
     * 封面图片 id
     */
    private Long cover;
}
