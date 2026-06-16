package hk.ljx.fishpicsbackend.mapper;

import hk.ljx.fishpicsbackend.space.entity.Space;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface SpaceMapper extends BaseMapper<Space> {

    // 原子 +delta，只在没超配额时成功，返回 0 说明空间满了
    @Update("UPDATE space SET size = size + #{delta} " +
            "WHERE id = #{spaceId} AND (storage_size IS NULL OR size + #{delta} <= storage_size)")
    int conditionalIncrementSize(@Param("spaceId") Long spaceId,
                                @Param("delta") long delta);
}
