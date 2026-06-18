package hk.ljx.fishpicsbackend.space.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 团队空间成员表
 */
@TableName("space_team_member")
@Data
public class SpaceTeamMember implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联 space.id
     */
    private Long spaceId;

    /**
     * 关联 user.id
     */
    private Long userId;

    /**
     * 关联 role.id（仅允许 1=所有者, 2=成员）
     */
    private Integer roleId;

    /**
     * 加入时间
     */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
