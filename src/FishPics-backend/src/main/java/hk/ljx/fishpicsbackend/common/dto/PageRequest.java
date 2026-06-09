package hk.ljx.fishpicsbackend.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PageRequest {
    /**
     * 当前页号
     */
    @Min(value = 1, message = "页码不能小于1")
    private int current = 1;

    /**
     * 页面大小
     */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页最多100条")
    private int pageSize = 20;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序顺序（默认降序）
     */
    private String sortOrder = "desc";
}
