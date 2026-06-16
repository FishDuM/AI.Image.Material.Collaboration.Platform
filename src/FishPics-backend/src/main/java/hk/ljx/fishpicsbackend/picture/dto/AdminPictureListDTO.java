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
     * 图片状态筛选: 0-禁用 1-正常 2-待审核 4-精选，不传则查全部
     */
    private Integer status;
}
