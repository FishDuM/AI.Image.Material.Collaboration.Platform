package hk.ljx.fishpicsbackend.dto.space;

import hk.ljx.fishpicsbackend.dto.base.PageRequest;
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

    /** 空间ID，必填 */
    private Long spaceId;
}
