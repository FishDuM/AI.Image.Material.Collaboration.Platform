package hk.ljx.fishpicsbackend.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 帖子表
 * @TableName post
 */
@TableName(value ="post")
@Data
public class Post implements Serializable {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
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
     * 是否删除 0-否 1-是
     */
    @TableLogic
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

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}