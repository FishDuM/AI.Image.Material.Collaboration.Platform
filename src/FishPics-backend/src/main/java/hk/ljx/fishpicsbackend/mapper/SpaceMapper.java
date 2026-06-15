package hk.ljx.fishpicsbackend.mapper;

import hk.ljx.fishpicsbackend.space.entity.Space;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @description 针对表【space(空间表)】的数据库操作Mapper
 */
public interface SpaceMapper extends BaseMapper<Space> {

    /**
     * 原子地 + delta 配额,仅当 size+delta <= storageSize 时成功
     *
     * @return affectedRows: 1=成功,0=配额不足
     */
    @Update("UPDATE space SET size = size + #{delta} " +
            "WHERE id = #{spaceId} AND size + #{delta} <= storage_size")
    int conditionalIncrementSize(@Param("spaceId") Long spaceId,
                                @Param("delta") long delta);
}
