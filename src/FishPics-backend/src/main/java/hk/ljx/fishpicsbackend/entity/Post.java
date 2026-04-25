package hk.ljx.fishpicsbackend.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.util.Date;
import lombok.Data;

/**
 * 帖子表
 */
@TableName(value ="post")
@Data
public class Post {
    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 关联用户表
     */
    @TableField("user_id")
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
     * 图片id数组
     */
    private String pictureIds;

    /**
     * 状态 1-正常 0-禁用 2-待审核
     */
    private Integer status;

    /**
     * 0-逻辑未删除, 1-逻辑删除
     */
    @TableLogic
    @TableField("`delete`")
    private Integer delete;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}