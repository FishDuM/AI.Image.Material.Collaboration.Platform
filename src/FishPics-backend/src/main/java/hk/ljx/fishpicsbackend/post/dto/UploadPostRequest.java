package hk.ljx.fishpicsbackend.post.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadPostRequest implements Serializable {

    /**
     * 关联的图片列表
     */
    private List<Long> imageId;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 封面图片索引（图片列表中的位置）
     */
    private Integer cover;

    /**
     * 0-公开，1-仅自己可见，
     */
    private Integer isPrivate;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
