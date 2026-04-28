package hk.ljx.fishpicsbackend.dto.post;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import hk.ljx.fishpicsbackend.dto.base.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

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

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
