package hk.ljx.fishpicsbackend.vo.comment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommentVO {

    /**
     * 评论ID
     */
    private Long id;

    /**
     * 评论人ID
     */
    private Long userId;

    /**
     * 评论人用户名
     */
    private String username;

    /**
     * 评论人头像
     */
    private String avatar;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 父评论ID
     */
    private Long parentId;

    /**
     * 回复给谁
     */
    private Integer toUserId;

    /**
     * 被回复人用户名
     */
    private String toUsername;

    /**
     * 状态 1-正常 0-禁用 2-待审核
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 二级回复列表
     */
    private List<CommentVO> replies;
}
