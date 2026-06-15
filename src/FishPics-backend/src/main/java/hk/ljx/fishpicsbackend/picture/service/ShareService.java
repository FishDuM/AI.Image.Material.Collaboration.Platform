package hk.ljx.fishpicsbackend.picture.service;

import java.util.List;
import java.util.Map;

import hk.ljx.fishpicsbackend.picture.vo.ShareFileVO;

public interface ShareService {

    /**
     * 创建分享链接（支持多图）
     *
     * @param pictureIds    图片ID列表
     * @param userId        分享人ID
     * @param expireDays    有效天数（最长7天）
     * @param allowDownload 是否允许下载
     * @param maxViewCount  最大访问次数（null/0=默认 200,上限 1000）
     * @return shareToken
     */
    String createShare(List<Long> pictureIds, Long userId, int expireDays, int allowDownload, Integer maxViewCount);

    /**
     * 获取分享信息
     *
     * @param shareToken 分享令牌
     * @return {pictures: [...], expireTime, allowDownload}
     */
    Map<String, Object> getShareInfo(String shareToken);

    /**
     * 预览分享中的某张图片
     *
     * @param shareToken 分享令牌
     * @param pictureId  图片ID（可选，单图或首次请求时可不传）
     * @return 图片文件流
     */
    ShareFileVO getPreviewFile(String shareToken, Long pictureId);

    /**
     * 下载分享中的某张图片
     *
     * @param shareToken 分享令牌
     * @param pictureId  图片ID（可选，单图或首次请求时可不传）
     * @return 图片文件流
     */
    ShareFileVO getDownloadFile(String shareToken, Long pictureId);

    /**
     * 取消分享
     *
     * @param shareId 分享ID
     * @param userId  操作人ID（只能取消自己创建的分享）
     */
    void cancelShare(Long shareId, Long userId);
}
