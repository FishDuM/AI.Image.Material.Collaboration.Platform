package hk.ljx.fishpicsbackend.common.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 通用 ID 请求参数
 */
@Data
public class IdRequest {

    /**
     * 业务ID
     */
    @NotNull(message = "ID不能为空")
    private Long id;
}
