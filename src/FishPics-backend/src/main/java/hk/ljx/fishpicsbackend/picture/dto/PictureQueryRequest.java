package hk.ljx.fishpicsbackend.picture.dto;

import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 图片查询请求 DTO
 * 支持按标签模糊搜索
 */
@EqualsAndHashCode(callSuper = true)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PictureQueryRequest extends PageRequest {

    /**
     * 标签关键词，可选，模糊匹配图片 tags 字段（逗号分隔）
     */
    private String tag;
}
