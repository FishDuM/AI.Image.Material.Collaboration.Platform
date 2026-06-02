package hk.ljx.fishpicsbackend.comment.dto;

import lombok.Data;

/**
 * 管理员审核评论请求
 */
@Data
public class ReviewCommentDTO {

    /**
     * 评论ID
     */
    private Long id;

    /**
     * 审核状态
     */
    private Integer status;
}
