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
    @TableId(type = IdType.AUTO)
    private Long id;

    private String md5;

    private Long size;

    private String cosKey;

    private Integer refCount;

    private LocalDateTime createTime;

    @Version
    private Integer version;

    private static final long serialVersionUID = 1L;
}
