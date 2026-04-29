package hk.ljx.fishpicsbackend.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户点赞帖子表
 * @TableName user_post_likes
 */
@TableName(value ="user_post_likes")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPostLikes implements Serializable {
    /**
     * 
     */
    @TableId
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