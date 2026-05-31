package hk.ljx.fishpicsbackend.post.dto;

import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PostQueryWrapper extends PageRequest implements Serializable {

    /**
     * 帖子 id
     */
    private Long id;

    /**
     * 关联用户
     */
    private Long userId;

    /**
     * 内容
     */
    private String text;

    /**
     * 状态 1-正常 0-禁用 2-待审核
     */
    private Integer status = 1;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否热门优先
     */
    private Boolean hotPost;

    /**
     * 0-公开，1-仅自己可见，
     */
    private Integer isPrivate;

    /**
     * 图片标签关键词，按图片标签筛选帖子
     */
    private String tag;

}
