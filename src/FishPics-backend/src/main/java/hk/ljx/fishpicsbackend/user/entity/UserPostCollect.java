package hk.ljx.fishpicsbackend.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Data;

/**
 * 用户帖子收藏表
 * @TableName user_post_collect
 */
@TableName(value ="user_post_collect")
@Data
public class UserPostCollect implements Serializable {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private Long userId;

    /**
     * 
     */
    private Long postId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}