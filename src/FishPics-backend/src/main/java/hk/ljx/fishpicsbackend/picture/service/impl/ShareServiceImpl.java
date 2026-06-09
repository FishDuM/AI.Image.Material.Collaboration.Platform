package hk.ljx.fishpicsbackend.picture.service.impl;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.utils.DownloadUtils;
import hk.ljx.fishpicsbackend.mapper.PictureShareMapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.entity.PictureShare;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.picture.service.ShareService;
import hk.ljx.fishpicsbackend.picture.vo.ShareFileVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class ShareServiceImpl implements ShareService {

    /** 分享代理最大文件大小：50MB，防止资源耗尽 */
    private static final long MAX_SHARE_PROXY_SIZE = 50L * 1024 * 1024;

    @Resource
    private PictureShareMapper pictureShareMapper;

    @Resource
    private PictureService pictureService;

    @Override
    public String createShare(Long pictureId, Long userId, int expireDays, int allowDownload) {
        Picture picture = pictureService.getById(pictureId);
        ExcUtils.throwIfTrue(picture == null, ExceptionCode.NOT_FOUND, "图片不存在");
        ExcUtils.throwIfTrue(!picture.getUserId().equals(userId), ExceptionCode.FORBIDDEN, "只能分享自己的图片");

        int actualExpireDays = Math.min(Math.max(expireDays, 1), 7);
        String shareToken = UUID.randomUUID().toString(true);
        Date expireTime = new Date(System.currentTimeMillis() + actualExpireDays * 86400_000L);

        PictureShare share = new PictureShare();
        share.setPictureId(pictureId);
        share.setShareUserId(userId);
        share.setShareToken(shareToken);
        share.setExpireTime(expireTime);
        share.setAllowDownload(allowDownload == 1 ? 1 : 0);
        share.setStatus(1);
        share.setCreateTime(new Date());
        pictureShareMapper.insert(share);

        log.info("create share pictureId={}, userId={}, token={}****",
                pictureId, userId, shareToken.substring(0, Math.min(8, shareToken.length())));
        return shareToken;
    }

    @Override
    public Map<String, Object> getShareInfo(String shareToken) {
        ShareResolved resolved = resolveShare(shareToken);
        Picture picture = resolved.picture();
        PictureShare share = resolved.share();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pictureName", picture.getPictureName());
        result.put("introduction", picture.getIntroduction());
        result.put("width", picture.getWidth());
        result.put("height", picture.getHeight());
        result.put("expireTime", share.getExpireTime());
        result.put("allowDownload", share.getAllowDownload());
        result.put("previewUrl", "/api/share/preview/" + shareToken);
        result.put("downloadUrl", Integer.valueOf(1).equals(share.getAllowDownload()) ? "/api/share/download/" + shareToken : null);
        return result;
    }

    @Override
    public ShareFileVO getPreviewFile(String shareToken) {
        ShareResolved resolved = resolveShare(shareToken);
        return buildShareFile(resolved.picture());
    }

    @Override
    public ShareFileVO getDownloadFile(String shareToken) {
        ShareResolved resolved = resolveShare(shareToken);
        ExcUtils.throwIfTrue(!Integer.valueOf(1).equals(resolved.share().getAllowDownload()),
                ExceptionCode.FORBIDDEN, "当前分享不允许下载");
        return buildShareFile(resolved.picture());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelShare(Long shareId, Long userId) {
        PictureShare share = pictureShareMapper.selectById(shareId);
        ExcUtils.throwIfTrue(share == null, ExceptionCode.NOT_FOUND, "分享记录不存在");
        ExcUtils.throwIfTrue(!share.getShareUserId().equals(userId), ExceptionCode.FORBIDDEN, "只能取消自己创建的分享");
        ExcUtils.throwIfTrue(Integer.valueOf(0).equals(share.getStatus()), ExceptionCode.PARAMETER_ERROR, "分享已取消");
        share.setStatus(0);
        pictureShareMapper.updateById(share);
        log.info("cancel share shareId={}, userId={}", shareId, userId);
    }

    private ShareResolved resolveShare(String shareToken) {
        PictureShare share = pictureShareMapper.selectOne(new LambdaQueryWrapper<PictureShare>()
                .eq(PictureShare::getShareToken, shareToken)
                .eq(PictureShare::getStatus, 1));
        ExcUtils.throwIfTrue(share == null, ExceptionCode.NOT_FOUND, "分享链接不存在或已失效");
        ExcUtils.throwIfTrue(share.getExpireTime() == null || share.getExpireTime().before(new Date()),
                ExceptionCode.FORBIDDEN, "分享链接已过期");

        Picture picture = pictureService.getById(share.getPictureId());
        ExcUtils.throwIfTrue(picture == null, ExceptionCode.NOT_FOUND, "图片已被删除");
        ExcUtils.throwIfTrue(picture.getUrl() == null || picture.getUrl().isBlank(),
                ExceptionCode.INTERNAL_SERVER_ERROR, "图片地址不存在");
        return new ShareResolved(share, picture);
    }

    private ShareFileVO buildShareFile(Picture picture) {
        DownloadUtils.RemoteFileStream remoteFile = DownloadUtils.openRemoteFile(picture.getUrl(), MAX_SHARE_PROXY_SIZE);
        return new ShareFileVO(
                picture.getPictureName(),
                remoteFile.getContentType(),
                remoteFile.getContentLength(),
                remoteFile.getInputStream(),
                remoteFile
        );
    }

    private record ShareResolved(PictureShare share, Picture picture) {
    }
}
