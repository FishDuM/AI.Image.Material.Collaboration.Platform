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

    // 1=正常 0=禁用 2=待审核
    private Integer status;
    // 0=公开 1=私有
    @JsonProperty("isPrivate")
    private Integer isPrivate;
    // 0=普通 1=精选
    @JsonProperty("isSelected")
    private Integer isSelected;

    private Long spaceId;
    private Long userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // ---- 工厂方法 ----

    // 列表页只返回 id + url + tags
    public static PictureVO ofList(Long id, String url, List<String> tags) {
        return PictureVO.builder()
                .id(id)
                .url(url)
                .tags(tags)
                .build();
    }

    // 编辑页
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

    // 上传完成返回
    public static PictureVO ofUpload(Long id, String url) {
        return PictureVO.builder()
                .id(id)
                .url(url)
                .build();
    }

    // 管理端，带完整信息
    public static PictureVO ofAdmin(Long id, String url, String width, String height,
                                     Long size, Integer status, LocalDateTime createTime,
                                     Long userId, Integer isPrivate, Integer isSelected,
                                     List<String> tags) {
        return PictureVO.builder()
                .id(id)
                .url(url)
                .width(width)
                .height(height)
                .size(size)
                .status(status)
                .createTime(createTime)
                .userId(userId)
                .isPrivate(isPrivate)
                .isSelected(isSelected)
                .tags(tags)
                .build();
    }
}
