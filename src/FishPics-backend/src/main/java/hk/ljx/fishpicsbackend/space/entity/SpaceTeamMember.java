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

    private Long spaceId;

    private Long userId;

    private Integer roleId;

    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
