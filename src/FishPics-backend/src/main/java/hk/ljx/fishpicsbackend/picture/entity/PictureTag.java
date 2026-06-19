package hk.ljx.fishpicsbackend.picture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 图片标签关联表
 * @TableName picture_tag
 */
@TableName(value = "picture_tag")
@Data
public class PictureTag implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pictureId;

    private String tagName;

    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
