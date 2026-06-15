package hk.ljx.fishpicsbackend.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import hk.ljx.fishpicsbackend.picture.entity.FileResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Update;

/**
 * 物理文件去重表 Mapper
 */
@Mapper
public interface FileResourceMapper extends BaseMapper<FileResource> {

    /**
     * 原子插入或更新引用计数（依赖 md5+size 唯一索引）
     * 不存在 → 插入新记录（ref_count=1）
     * 已存在 → ref_count + 1
     */
    @InterceptorIgnore(blockAttack = "true")
    @Update("INSERT INTO file_resource (md5, size, cos_key, ref_count, create_time) " +
            "VALUES (#{md5}, #{size}, #{cosKey}, 1, NOW()) " +
            "ON DUPLICATE KEY UPDATE ref_count = ref_count + 1")
    int upsertByMd5Size(@Param("md5") String md5, @Param("size") Long size, @Param("cosKey") String cosKey);

    /**
     * 原子递增引用计数
     */
    @Update("UPDATE file_resource SET ref_count = ref_count + 1 WHERE id = #{id}")
    int incrementRefCountAtomic(@Param("id") Long id);

    /**
     * 原子递减引用计数，返回受影响行数（0 表示记录不存在）
     */
    @Update("UPDATE file_resource SET ref_count = ref_count - 1 WHERE id = #{id} AND ref_count > 0")
    int decrementRefCountAtomic(@Param("id") Long id);

    /**
     * 查询当前引用计数
     */
    @Select("SELECT ref_count FROM file_resource WHERE id = #{id}")
    Integer getRefCount(@Param("id") Long id);

    /**
     * 原子标记删除：仅当 ref_count=0 时删除记录，返回受影响行数
     */
    @Delete("DELETE FROM file_resource WHERE id = #{id} AND ref_count <= 0")
    int deleteIfZeroRef(@Param("id") Long id);
}
