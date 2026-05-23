package hk.ljx.fishpicsbackend.dto.comment;

import com.baomidou.mybatisplus.annotation.TableField;
import hk.ljx.fishpicsbackend.dto.base.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommentQueryRequest extends PageRequest implements Serializable {

    /**
     * 帖子ID
     */
    private Long postId;

    /**
     * 状态筛选 (管理员用)
     */
    private Integer status;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
