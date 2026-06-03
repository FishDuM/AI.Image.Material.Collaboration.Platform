package hk.ljx.fishpicsbackend.space.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新空间信息请求DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSpace {

    // 空间ID，必填
    private Long id;

    // 新空间名称，必填
    private String name;

    // 新空间介绍
    private String introduction;
}
