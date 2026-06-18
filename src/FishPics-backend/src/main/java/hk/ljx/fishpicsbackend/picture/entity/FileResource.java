package hk.ljx.fishpicsbackend.picture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 物理文件去重表
 * @TableName file_resource
 */
@TableName(value = "file_resource")
@Data
public class FileResource implements Serializable {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件MD5
     */
    private String md5;

    /**
     * 文件大小(Byte)
     */
    private Long size;

    /**
     * COS存储路径
     */
    private String cosKey;

    /**
     * 引用计数
     */
    private Integer refCount;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    private static final long serialVersionUID = 1L;
}
