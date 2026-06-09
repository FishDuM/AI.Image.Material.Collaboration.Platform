package hk.ljx.fishpicsbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember;
import org.apache.ibatis.annotations.Mapper;

/**
 * 团队空间成员 Mapper 接口
 */
@Mapper
public interface SpaceTeamMemberMapper extends BaseMapper<SpaceTeamMember> {
}
