package hk.ljx.fishpicsbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;

import lombok.Builder;
import lombok.Data;

/**
 * 帖子表
 * @TableName post
 */
@TableName(value ="post")
@Data
@Builder
public class Post implements Serializable {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联发帖用户
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

    /**
     * 查看数
     */
    private Long viewsNum;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}