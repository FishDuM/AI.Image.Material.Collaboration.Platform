package hk.ljx.fishpicsbackend.picture.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ShareCreateRequest {

    @NotNull(message = "pictureIds不能为空")
    @Size(min = 1, message = "至少选择一张图片")
    private List<Long> pictureIds;

    /**
     * 过期天数，默认1天
     */
    private Integer expireDays = 1;

    /**
     * 是否允许下载 1-允许 0-不允许
     */
    private Integer allowDownload = 1;

    /**
     * 最大访问次数(0 或 null = 不限,默认 200)
     */
    private Integer maxViewCount;
}
