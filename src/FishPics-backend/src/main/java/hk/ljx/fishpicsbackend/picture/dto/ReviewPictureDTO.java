package hk.ljx.fishpicsbackend.picture.dto;

import lombok.Data;

/**
 * 管理员审核精选请求
 */
@Data
public class ReviewPictureDTO {

    /**
     * 图片ID
     */
    private Long pictureId;

    /**
     * 是否精选: 1-精选 0-取消精选
     */
    private Integer selected;
}
