package hk.ljx.fishpicsbackend.picture.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ShareCreateRequest {

    @NotNull(message = "pictureId不能为空")
    private Long pictureId;

    /**
     * 过期天数，默认1天
     */
    private Integer expireDays = 1;

    /**
     * 是否允许下载 1-允许 0-不允许
     */
    private Integer allowDownload = 1;
}
