package hk.ljx.fishpicsbackend.vo.post;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostListVO {
    /**
     * 主键
     */
    private Long id;

    /**
     * 关联用户表
     */
    private Long userId;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 封面图片的 id
     */
    private Long cover;

    /**
     * 图片id数组
     */
    private List<Long> pictureIds;

    /**
     * 创建时间
     */
    private Date createTime;
}
