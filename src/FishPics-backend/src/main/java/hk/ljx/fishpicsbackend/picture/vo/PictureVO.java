package hk.ljx.fishpicsbackend.picture.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

// 列表/详情/上传/管理端复用，@JsonInclude(NON_NULL) 控制返回字段
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PictureVO {

    private Long id;
    private String url;
    private String pictureName;
    private String introduction;
    private List<String> tags;
    private String width;
    private String height;
    private Long size;
    private String type;

    @JsonProperty("isSelected")
    private Integer isSelected;

    private Long spaceId;
    private Long userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ---- 工厂方法 ----

    public static PictureVO ofList(Long id, String url, List<String> tags) {
        return PictureVO.builder()
                .id(id)
                .url(url)
                .tags(tags)
                .build();
    }

    public static PictureVO ofDetail(Long id, String url, String pictureName,
                                      String introduction, List<String> tags) {
        return PictureVO.builder()
                .id(id)
                .url(url)
                .pictureName(pictureName)
                .introduction(introduction)
                .tags(tags)
                .build();
    }

    public static PictureVO ofUpload(Long id, String url) {
        return PictureVO.builder()
                .id(id)
                .url(url)
                .build();
    }

    public static PictureVO ofAdmin(Long id, String url, String width, String height,
                                     Long size, LocalDateTime createTime,
                                     Long userId, Integer isSelected,
                                     List<String> tags) {
        return PictureVO.builder()
                .id(id)
                .url(url)
                .width(width)
                .height(height)
                .size(size)
                .createTime(createTime)
                .userId(userId)
                .isSelected(isSelected)
                .tags(tags)
                .build();
    }
}
