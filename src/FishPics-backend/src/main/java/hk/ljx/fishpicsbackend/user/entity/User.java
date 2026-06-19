package hk.ljx.fishpicsbackend.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;

import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户表
 * @TableName user
 */
@TableName(value ="user")
@Data
public class User implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String avatar;

    private String email;

    private String phone;

    private String nickname;

    /** 状态 1-正常 0-禁用 2-待审核 */
    private Integer status;

    /** 用户等级 0=普通 1=VIP 2=SVIP */
    private Integer level;

    /** 用户角色 0=普通 1=管理员 */
    private Integer role;

    /** 0-逻辑未删除, 1-逻辑删除 */
    @TableLogic
    private Integer isDelete;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private static final long serialVersionUID = 1L;

    public boolean isActive() {
        return status != null && ExcUtils.eq(status, 1);
    }
}