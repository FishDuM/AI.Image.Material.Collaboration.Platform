package hk.ljx.fishpicsbackend.picture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片分享表
 */
@TableName(value = "picture_share")
@Data
public class PictureShare implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 图片ID */
    private Long pictureId;

    /** 分享人ID */
    private Long shareUserId;

    /** 分享令牌 */
    private String shareToken;

    /** 过期时间 */
    private Date expireTime;

    /** 是否允许下载 0=仅预览 1=允许 */
    private Integer allowDownload;

    /** 状态 1=有效 0=已取消 */
    private Integer status;

    /** 创建时间 */
    private Date createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
