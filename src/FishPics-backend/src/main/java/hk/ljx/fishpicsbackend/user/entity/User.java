package hk.ljx.fishpicsbackend.user.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 用户表
 * @TableName user
 */
@TableName(value ="user")
@Data
public class User implements Serializable {
    /**
     * 用户ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名（登录用）
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 昵称（展示用）
     */
    private String nickname;

    /**
     * 状态 1-正常 0-禁用 2-待审核
     */
    private Integer status;

    /**
     * 0-逻辑未删除, 1-逻辑删除
     */
    @TableLogic
    private Integer isDelete;

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
    private Long likeNum;

    /**
     * 
     */
    private Long collectNum;

    /**
     * 0-公开关注列表，1-不公开关注列表
     */
    private Integer isPrivateFollows;

    /**
     * 0-公开帖子列表，1-不公开帖子列表
     */
    private Integer isPrivatePostCollect;

    /**
     * 0-公开点赞帖子列表，1-不公开点赞帖子列表
     */
    private Integer isPrivateLikes;

    /**
     * 0-公开粉丝列表，1-不公开粉丝列表
     */
    private Integer isPrivateFans;

    /**
     * 0-普通，1-VIP，2-SVIP
     */
    private Integer level;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}