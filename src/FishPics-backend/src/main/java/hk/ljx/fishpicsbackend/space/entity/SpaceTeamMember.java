package hk.ljx.fishpicsbackend.space.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 团队空间成员表
 */
@TableName("space_team_member")
@Data
public class SpaceTeamMember implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 团队空间ID
    private Long spaceId;

    // 用户ID
    private Long userId;

    // 团队内角色ID，关联 sys_role
    private Long roleId;

    private Date joinedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
