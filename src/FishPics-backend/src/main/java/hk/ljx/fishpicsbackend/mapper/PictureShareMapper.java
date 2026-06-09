package hk.ljx.fishpicsbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import hk.ljx.fishpicsbackend.picture.entity.PictureShare;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PictureShareMapper extends BaseMapper<PictureShare> {
}
