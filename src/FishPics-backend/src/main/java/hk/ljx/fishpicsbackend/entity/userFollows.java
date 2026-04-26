package hk.ljx.fishpicsbackend.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import lombok.Data;

/**
 * 用户关注表
 * @TableName likes_user_by_id
 */
@TableName(value ="likes_user_by_id")
@Data
public class userFollows implements Serializable {
    /**
     * 
     */
    private Long id;

    /**
     * 
     */
    private Long userId;

    /**
     * 
     */
    private Long beFollowedUserId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}