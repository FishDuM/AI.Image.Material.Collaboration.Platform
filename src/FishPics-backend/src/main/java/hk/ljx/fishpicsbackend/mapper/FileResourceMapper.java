package hk.ljx.fishpicsbackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import hk.ljx.fishpicsbackend.picture.entity.FileResource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 物理文件去重表 Mapper
 */
@Mapper
public interface FileResourceMapper extends BaseMapper<FileResource> {
}
