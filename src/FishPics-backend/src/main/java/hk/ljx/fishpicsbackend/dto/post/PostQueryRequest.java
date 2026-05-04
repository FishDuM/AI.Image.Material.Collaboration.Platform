package hk.ljx.fishpicsbackend.dto.post;

import com.baomidou.mybatisplus.annotation.TableField;
import hk.ljx.fishpicsbackend.dto.base.PageRequest;
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
public class PostQueryRequest extends PageRequest implements Serializable {

    /**
     * 关联用户表
     */
    private Long userId;

    /**
     * 内容
     */
    private String text;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否热门优先
     */
    private Boolean hotPost;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
