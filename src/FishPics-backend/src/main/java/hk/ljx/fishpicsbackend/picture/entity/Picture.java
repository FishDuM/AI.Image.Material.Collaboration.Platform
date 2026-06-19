package hk.ljx.fishpicsbackend.picture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 图片表
 * @TableName picture
 */
@TableName(value ="picture")
@Data
public class Picture implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String pictureName;

    private String url;

    private String width;

    private String height;

    private Long size;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long spaceId;

    /** 关联file_resource.id（文件去重） */
    private Long resourceId;

    private String introduction;

    private String type;

    /** 是否精选: 0=普通 1=精选 2=申请中 */
    private Integer isSelected;

    @Version
    private Integer version;

    private static final long serialVersionUID = 1L;
}
