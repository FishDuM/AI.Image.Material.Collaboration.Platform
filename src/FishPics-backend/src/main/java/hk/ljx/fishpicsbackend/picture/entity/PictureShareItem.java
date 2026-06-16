package hk.ljx.fishpicsbackend.picture.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@TableName(value = "picture_share_item")
@Data
public class PictureShareItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shareId;

    private Long pictureId;

    private Integer sortOrder;

    private static final long serialVersionUID = 1L;
}
