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

    /** 图片ID */
    private Long pictureId;

    /** 分享人ID */
    private Long shareUserId;

    /** 分享令牌(明文,只在创建时返回给调用方一次) */
    private String shareToken;

    /**
     * token 哈希
     */
    private String shareTokenHash;

    /** 过期时间 */
    private LocalDateTime expireTime;

    /** 是否允许下载 0=仅预览 1=允许 */
    private Integer allowDownload;

    /** 状态 1=有效 0=已取消 */
    private Integer status;

    /** 最大访问次数(0=不限) */
    private Integer maxViewCount;

    /** 当前已访问次数(运行时维护) */
    @TableField(exist = false)
    private Integer viewCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    private static final long serialVersionUID = 1L;
}
