package hk.ljx.fishpicsbackend.picture.dto;

import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理员图片列表查询请求
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminPictureListDTO extends PageRequest {

    /**
     * 精选状态筛选: 0-普通 1-已精选 2-精选申请中，不传则查全部
     */
    private Integer selected;
}
