package hk.ljx.fishpicsbackend.space.dto;

import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 空间图片列表查询请求DTO（继承PageRequest，支持分页排序）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
public class SpacePictureList extends PageRequest implements Serializable {

    // 空间ID，必填
    private Long spaceId;

    // 搜索关键词，可选，模糊匹配图片名称和介绍
    private String keyword;
}
