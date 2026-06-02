package hk.ljx.fishpicsbackend.post.vo;

import hk.ljx.fishpicsbackend.picture.vo.PictureListByEditPostVO;
import lombok.Data;

import java.util.List;

/**
 * 编辑帖子时的图片分页列表
 */
@Data
public class PictureListPageVO {

    /**
     * 图片列表
     */
    private List<PictureListByEditPostVO> records;

    /**
     * 总数
     */
    private Long total;
}
