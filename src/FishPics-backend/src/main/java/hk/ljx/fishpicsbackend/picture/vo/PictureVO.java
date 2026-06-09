package hk.ljx.fishpicsbackend.picture.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 统一图片 VO
 * 合并了原来的 PictureListVO、PictureEditVO、PicturePostVO
 *
 * 设计原则：
 * - 使用 @JsonInclude 控制不同场景返回不同字段
 * - 简化前端接口，一个 VO 适配多种场景
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)  // 只返回非空字段
public class PictureVO {

    /**
     * 图片ID
     */
    private Long id;

    /**
     * 图片URL
     */
    private String url;

    /**
     * 图片名称
     */
    private String pictureName;

    /**
     * 图片介绍
     */
    private String introduction;

    /**
     * 图片标签
     */
    private List<String> tags;

    /**
     * 图片宽度
     */
    private String width;

    /**
     * 图片高度
     */
    private String height;

    /**
     * 文件大小（字节）
     */
    private Long size;

    /**
     * 图片格式
     */
    private String type;

    /**
     * 状态：1=正常 0=禁用 2=待审核
     */
    private Integer status;

    /**
     * 是否公开：0=不公开 1=公开
     */
    private Integer isPrivate;

    /**
     * 是否精选：0=普通 1=精选
     */
    private Integer isSelected;

    /**
     * 所属空间ID
     */
    private Long spaceId;

    /**
     * 上传者用户ID
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    // ==================== 静态工厂方法 ====================

    /**
     * 创建图片列表 VO（用于图片列表、分页查询）
     */
    public static PictureVO ofList(Long id, String url, List<String> tags) {
        return PictureVO.builder()
                .id(id)
                .url(url)
                .tags(tags)
                .build();
    }

    /**
     * 创建图片详情 VO（用于编辑图片）
     */
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

    /**
     * 创建图片上传响应 VO
     */
    public static PictureVO ofUpload(Long id, String url) {
        return PictureVO.builder()
                .id(id)
                .url(url)
                .build();
    }

    /**
     * 创建管理员查看的 VO（包含完整信息）
     */
    public static PictureVO ofAdmin(Long id, String url, String width, String height,
                                     Long size, Integer status, Date createTime,
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

    /**
     * 创建完整信息 VO（包含所有字段）
     */
    public static PictureVO ofFull(Long id, String url, String pictureName,
                                    String introduction, List<String> tags,
                                    String width, String height, Long size,
                                    String type, Integer status, Integer isPrivate,
                                    Integer isSelected, Long spaceId, Long userId,
                                    Date createTime, Date updateTime) {
        return PictureVO.builder()
                .id(id)
                .url(url)
                .pictureName(pictureName)
                .introduction(introduction)
                .tags(tags)
                .width(width)
                .height(height)
                .size(size)
                .type(type)
                .status(status)
                .isPrivate(isPrivate)
                .isSelected(isSelected)
                .spaceId(spaceId)
                .userId(userId)
                .createTime(createTime)
                .updateTime(updateTime)
                .build();
    }
}
