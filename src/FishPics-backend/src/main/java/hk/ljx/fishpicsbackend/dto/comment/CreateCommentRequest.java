package hk.ljx.fishpicsbackend.dto.comment;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateCommentRequest implements Serializable {

    /**
     * 关联帖子ID
     */
    private Long postId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 父评论ID（回复时填，一级评论不填）
     */
    private Long parentId;

    /**
     * 回复给谁（回复时填）
     */
    private Integer toUserId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
