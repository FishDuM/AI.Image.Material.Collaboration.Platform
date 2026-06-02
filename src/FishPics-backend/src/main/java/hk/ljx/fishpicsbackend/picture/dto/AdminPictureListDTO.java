package hk.ljx.fishpicsbackend.picture.dto;

import lombok.Data;

/**
 * 管理员图片列表查询请求
 */
@Data
public class AdminPictureListDTO {

    /**
     * 当前页号
     */
    private int current = 1;

    /**
     * 页面大小
     */
    private int pageSize = 20;

    /**
     * 图片状态筛选: 0-禁用 1-正常 2-待审核 4-精选，不传则查全部
     */
    private Integer status;
}
