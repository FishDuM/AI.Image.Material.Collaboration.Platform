package hk.ljx.fishpicsbackend.post.dto;

import lombok.Data;

/**
 * 管理员审核帖子请求
 */
@Data
public class ReviewPostDTO {

    /**
     * 帖子ID
     */
    private Long id;

    /**
     * 审核状态
     */
    private Integer status;
}
