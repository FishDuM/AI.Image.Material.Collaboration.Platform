package hk.ljx.fishpicsbackend.picture.service;

import java.util.Map;

import hk.ljx.fishpicsbackend.picture.vo.ShareFileVO;

public interface ShareService {

    /**
     * 创建分享链接
     *
     * @param pictureId     图片ID
     * @param userId        分享人ID
     * @param expireDays    有效天数（最长7天）
     * @param allowDownload 是否允许下载
     * @return shareToken
     */
    String createShare(Long pictureId, Long userId, int expireDays, int allowDownload);

    /**
     * 获取分享信息（含预签名 URL）
     *
     * @param shareToken 分享令牌
     * @return {picture, presignedUrl, expireTime, allowDownload}
     */
    Map<String, Object> getShareInfo(String shareToken);

    ShareFileVO getPreviewFile(String shareToken);

    ShareFileVO getDownloadFile(String shareToken);

    /**
     * 取消分享
     *
     * @param shareId 分享ID
     * @param userId  操作人ID（只能取消自己创建的分享）
     */
    void cancelShare(Long shareId, Long userId);
}
