package hk.ljx.fishpicsbackend.picture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 图片分享表
 */
@TableName(value = "picture_share")
@Data
public class PictureShare implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long pictureId;

    private Long shareUserId;

    private String shareToken;

    private String shareTokenHash;

    private LocalDateTime expireTime;

    private Integer allowDownload;

    private Integer status;

    private Integer maxViewCount;

    @TableField(exist = false)
    private Integer viewCount;

    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
