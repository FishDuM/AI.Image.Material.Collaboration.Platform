package hk.ljx.fishpicsbackend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;

import lombok.Data;

/**
 * 子图片表
 *
 * @TableName picture_child
 */
@TableName(value = "picture_child")
@Data
public class PictureChild implements Serializable {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联图片id
     */
    private Long pictureId;

    /**
     * 关联帖子id
     */
    private Long postId;

    /**
     * 在帖子中的顺序
     */
    private Integer sortNum;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}