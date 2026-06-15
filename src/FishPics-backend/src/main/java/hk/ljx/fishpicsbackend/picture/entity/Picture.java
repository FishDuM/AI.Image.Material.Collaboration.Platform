package hk.ljx.fishpicsbackend.picture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 图片表
 * @TableName picture
 */
@TableName(value ="picture")
@Data
public class Picture implements Serializable {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户id
     */
    private Long userId;

    /**
     * 图片名称
     */
    private String pictureName;

    /**
     * 图片地址
     */
    private String url;

    /**
     * 宽度
     */
    private String width;

    /**
     * 高度
     */
    private String height;

    /**
     * 大小
     */
    private Long size;

    /**
     * 状态 1-正常 0-禁用 2-待审核
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否私有: 0-公开 1-私有
     */
    private Integer isPrivate;

    /**
     * 空间Id
     */
    private Long spaceId;

    /**
     * 关联file_resource.id（文件去重）
     */
    private Long resourceId;

    /**
     * 图片介绍
     */
    private String introduction;

    /**
     * 图片格式
     */
    private String type;

    /**
     * 是否精选: 1-精选 0-普通
     */
    private Integer isSelected;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}