package hk.ljx.fishpicsbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 帖子表
 * @TableName post
 */
@TableName(value ="post")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Post implements Serializable {
    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联用户表
     */
    private Long userId;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 状态 1-正常 0-禁用 2-待审核
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 
     */
    private Integer isDelete;

    /**
     * 点赞数
     */
    private Long likesNum;

    /**
     * 收藏数
     */
    private Long collectsNum;

    /**
     * 评论数
     */
    private Integer commentNum;

    /**
     * 0-公开，1-仅自己可见，
     */
    private Integer isPrivate;

    /**
     * 封面图片的id
     */
    private Long cover;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}