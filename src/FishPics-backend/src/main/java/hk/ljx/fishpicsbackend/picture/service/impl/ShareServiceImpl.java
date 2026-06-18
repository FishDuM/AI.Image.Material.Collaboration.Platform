package hk.ljx.fishpicsbackend.picture.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.infra.RedisAtomicOps;
import hk.ljx.fishpicsbackend.common.utils.DownloadUtils;
import hk.ljx.fishpicsbackend.common.utils.IpUtils;
import hk.ljx.fishpicsbackend.mapper.PictureShareItemMapper;
import hk.ljx.fishpicsbackend.mapper.PictureShareMapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.entity.PictureShare;
import hk.ljx.fishpicsbackend.picture.entity.PictureShareItem;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.picture.service.ShareService;
import hk.ljx.fishpicsbackend.picture.vo.ShareFileVO;
import hk.ljx.fishpicsbackend.picture.vo.ShareInfoVO;
import hk.ljx.fishpicsbackend.picture.vo.SharePictureVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShareServiceImpl implements ShareService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] TOKEN_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();
    private static final String TOKEN_PATTERN = "^[A-Za-z0-9_-]{43}$";

    private static final long MAX_SHARE_PROXY_SIZE = 50L * 1024 * 1024;
    private static final long SHARE_VIEW_RATE_LIMIT_PER_MIN = 5;
    private static final long SHARE_VIEW_RATE_WINDOW_SEC = 60;
    private static final int DEFAULT_MAX_VIEW_COUNT = 200;

    @Resource
    private PictureShareMapper pictureShareMapper;

    @Resource
    private PictureShareItemMapper pictureShareItemMapper;

    @Resource
    private PictureService pictureService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisAtomicOps redisAtomicOps;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createShare(List<Long> pictureIds, Long userId, int expireDays,
                              int allowDownload, Integer maxViewCount) {
        ExcUtils.throwIfTrue(pictureIds == null || pictureIds.isEmpty(),
                ExceptionCode.PARAMETER_ERROR, "至少选择一张图片");

        List<Picture> pictures = pictureService.listByIds(pictureIds);
        ExcUtils.throwIfTrue(pictures.size() != pictureIds.size(),
                ExceptionCode.NOT_FOUND, "部分图片不存在");
        for (Picture picture : pictures) {
            ExcUtils.throwIfTrue(!picture.getUserId().equals(userId),
                    ExceptionCode.FORBIDDEN, "只能分享自己的图片");
        }

        int actualExpireDays = Math.min(Math.max(expireDays, 1), 7);
        int actualMaxView = (maxViewCount == null || maxViewCount <= 0)
                ? DEFAULT_MAX_VIEW_COUNT
                : Math.min(maxViewCount, 1000);
        String shareToken = generateSecureToken();
        String tokenHash = hashShareToken(shareToken);

        PictureShare share = new PictureShare();
        share.setPictureId(pictureIds.get(0));
        share.setShareToken(tokenHash);
        share.setShareTokenHash(tokenHash);
        share.setShareUserId(userId);
        share.setExpireTime(LocalDateTime.now().plusDays(actualExpireDays));
        share.setAllowDownload(allowDownload == 1 ? 1 : 0);
        share.setStatus(1);
        share.setMaxViewCount(actualMaxView);
        share.setCreateTime(LocalDateTime.now());
        pictureShareMapper.insert(share);

        for (int i = 0; i < pictureIds.size(); i++) {
            PictureShareItem item = new PictureShareItem();
            item.setShareId(share.getId());
            item.setPictureId(pictureIds.get(i));
            item.setSortOrder(i);
            pictureShareItemMapper.insert(item);
        }

        log.info("create share pictureIds={}, userId={}, token={}****, count={}, maxView={}",
                pictureIds, userId, shareToken.substring(0, 8), pictureIds.size(), actualMaxView);
        return shareToken;
    }

    private String generateSecureToken() {
        char[] token = new char[43];
        for (int i = 0; i < token.length; i++) {
            token[i] = TOKEN_CHARS[SECURE_RANDOM.nextInt(TOKEN_CHARS.length)];
        }
        return new String(token);
    }

    private String hashShareToken(String token) {
        return token == null ? null : DigestUtil.sha256Hex(token);
    }

    @Override
    public ShareInfoVO getShareInfo(String shareToken) {
        ShareResolved resolved = resolveShare(shareToken);
        PictureShare share = resolved.share();
        List<Picture> pictures = resolvePictures(share);

        List<SharePictureVO> pictureList = new ArrayList<>(pictures.size());
        for (Picture picture : pictures) {
            String previewUrl = "/api/share/preview/" + shareToken + "?pictureId=" + picture.getId();
            String downloadUrl = ExcUtils.eq(share.getAllowDownload(), 1)
                    ? "/api/share/download/" + shareToken + "?pictureId=" + picture.getId()
                    : null;
            pictureList.add(new SharePictureVO(
                    picture.getId(),
                    picture.getPictureName(),
                    picture.getIntroduction(),
                    picture.getWidth(),
                    picture.getHeight(),
                    previewUrl,
                    downloadUrl));
        }

        return new ShareInfoVO(share.getExpireTime(), share.getAllowDownload(), pictureList);
    }

    @Override
    public ShareFileVO getPreviewFile(String shareToken, Long pictureId) {
        ShareResolved resolved = resolveShare(shareToken);
        Picture picture = resolveTargetPicture(resolved.share(), pictureId);
        countShareView(resolved, shareToken);
        return buildShareFile(resolved.share(), picture);
    }

    @Override
    public ShareFileVO getDownloadFile(String shareToken, Long pictureId) {
        ShareResolved resolved = resolveShare(shareToken);
        ExcUtils.throwIfTrue(!ExcUtils.eq(resolved.share().getAllowDownload(), 1),
                ExceptionCode.FORBIDDEN, "当前分享不允许下载");
        Picture picture = resolveTargetPicture(resolved.share(), pictureId);
        countShareView(resolved, shareToken);
        return buildShareFile(resolved.share(), picture);
    }

    private Picture resolveTargetPicture(PictureShare share, Long pictureId) {
        if (pictureId != null) {
            PictureShareItem item = pictureShareItemMapper.selectOne(
                    new LambdaQueryWrapper<PictureShareItem>()
                            .eq(PictureShareItem::getShareId, share.getId())
                            .eq(PictureShareItem::getPictureId, pictureId)
                            .last("LIMIT 1"));
            ExcUtils.throwIfTrue(item == null, ExceptionCode.NOT_FOUND, "该图片不属于此分享");
            Picture picture = pictureService.getById(pictureId);
            ExcUtils.throwIfTrue(picture == null, ExceptionCode.NOT_FOUND, "图片已被删除");
            return picture;
        }

        List<Picture> pictures = resolvePictures(share);
        ExcUtils.throwIfTrue(pictures.isEmpty(), ExceptionCode.NOT_FOUND, "分享中没有图片");
        return pictures.get(0);
    }

    private List<Picture> resolvePictures(PictureShare share) {
        List<PictureShareItem> items = pictureShareItemMapper.selectList(
                new LambdaQueryWrapper<PictureShareItem>()
                        .eq(PictureShareItem::getShareId, share.getId())
                        .orderByAsc(PictureShareItem::getSortOrder));
        if (items.isEmpty()) {
            Picture picture = pictureService.getById(share.getPictureId());
            return picture == null ? List.of() : List.of(picture);
        }

        List<Long> pictureIds = items.stream()
                .map(PictureShareItem::getPictureId)
                .collect(Collectors.toList());
        Map<Long, Picture> pictureMap = pictureService.listByIds(pictureIds).stream()
                .collect(Collectors.toMap(Picture::getId, picture -> picture));

        List<Picture> ordered = new ArrayList<>(pictureIds.size());
        for (Long pictureId : pictureIds) {
            Picture picture = pictureMap.get(pictureId);
            if (picture != null) {
                ordered.add(picture);
            }
        }
        return ordered;
    }

    private long incrementViewCount(String tokenHash, LocalDateTime expireTime, int maxViewCount) {
        String viewCountKey = "SHARE:VIEW:COUNT:" + tokenHash;
        long ttlSec = 86400L;
        if (expireTime != null) {
            long remainingMs = Math.max(ChronoUnit.MILLIS.between(LocalDateTime.now(), expireTime) + 3600_000L, 60_000L);
            ttlSec = remainingMs / 1000L;
        }
        return redisAtomicOps.incrWithCheckAndRollback(viewCountKey, ttlSec, maxViewCount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelShare(Long shareId, Long userId) {
        PictureShare share = pictureShareMapper.selectById(shareId);
        ExcUtils.throwIfTrue(share == null, ExceptionCode.NOT_FOUND, "分享记录不存在");
        ExcUtils.throwIfTrue(!share.getShareUserId().equals(userId),
                ExceptionCode.FORBIDDEN, "只能取消自己创建的分享");
        ExcUtils.throwIfTrue(ExcUtils.eq(share.getStatus(), 0),
                ExceptionCode.PARAMETER_ERROR, "分享已取消");

        share.setStatus(0);
        pictureShareMapper.updateById(share);
        try {
            stringRedisTemplate.delete("SHARE:VIEW:COUNT:" + share.getShareTokenHash());
            stringRedisTemplate.delete("SHARE:VIEW:RATE:" + share.getShareTokenHash());
        } catch (Exception e) {
            log.warn("cancelShare cleanup redis keys failed: shareId={}, error={}", shareId, e.getMessage());
        }
        log.info("cancel share shareId={}, userId={}", shareId, userId);
    }

    private ShareResolved resolveShare(String shareToken) {
        ExcUtils.throwIfTrue(shareToken == null || !shareToken.matches(TOKEN_PATTERN),
                ExceptionCode.PARAMETER_ERROR, "invalid share token");
        String tokenHash = hashShareToken(shareToken);
        PictureShare share = pictureShareMapper.selectOne(new LambdaQueryWrapper<PictureShare>()
                .eq(PictureShare::getShareTokenHash, tokenHash)
                .eq(PictureShare::getStatus, 1)
                .last("LIMIT 1"));
        ExcUtils.throwIfTrue(share == null, ExceptionCode.NOT_FOUND, "分享链接不存在或已失效");
        ExcUtils.throwIfTrue(share.getExpireTime() == null || share.getExpireTime().isBefore(LocalDateTime.now()),
                ExceptionCode.FORBIDDEN, "分享链接已过期");
        return new ShareResolved(share, tokenHash);
    }

    private void countShareView(ShareResolved resolved, String shareToken) {
        String tokenHash = resolved.tokenHash();
        PictureShare share = resolved.share();

        String rateKey = "SHARE:VIEW:RATE:" + tokenHash;
        long currentCount = redisAtomicOps.incrWithExpire(rateKey, SHARE_VIEW_RATE_WINDOW_SEC);
        ExcUtils.throwIfTrue(currentCount > SHARE_VIEW_RATE_LIMIT_PER_MIN,
                ExceptionCode.TOO_MANY_REQUESTS, "访问过于频繁,请稍后再试");

        Integer maxView = share.getMaxViewCount() != null ? share.getMaxViewCount() : DEFAULT_MAX_VIEW_COUNT;
        long newView = incrementViewCount(tokenHash, share.getExpireTime(), maxView);
        if (newView == -1L) {
            share.setStatus(0);
            try {
                pictureShareMapper.updateById(share);
            } catch (Exception dbEx) {
                log.error("share auto-cancel DB update failed: shareId={}", share.getId(), dbEx);
            }
            log.info("share auto-cancelled shareId={}, token={}****, max={}",
                    share.getId(), shareToken.substring(0, 8), maxView);
            throw new BaseException(ExceptionCode.FORBIDDEN, "分享链接已达最大访问次数");
        }
        log.debug("share view+1: shareId={}, token={}****, views={}/{}",
                share.getId(), shareToken.substring(0, 8), newView, maxView);
    }

    private void recordShareAccess(PictureShare share) {
        try {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attr == null) {
                return;
            }
            String ip = IpUtils.getClientIp(attr.getRequest());
            String userAgent = attr.getRequest().getHeader("User-Agent");
            if (userAgent != null && userAgent.length() > 256) {
                userAgent = userAgent.substring(0, 256);
            }

            Map<String, Object> logEntry = new LinkedHashMap<>();
            logEntry.put("ts", System.currentTimeMillis());
            logEntry.put("ip", ip);
            logEntry.put("ua", userAgent);
            String key = "SHARE:ACCESS:LOG:" + share.getId();
            stringRedisTemplate.opsForList().leftPush(key, JSONUtil.toJsonStr(logEntry));
            stringRedisTemplate.opsForList().trim(key, 0, 99);
            stringRedisTemplate.expire(key, Duration.ofDays(7));
        } catch (Exception e) {
            log.warn("record share access failed: {}", e.getMessage());
        }
    }

    private ShareFileVO buildShareFile(PictureShare share, Picture picture) {
        DownloadUtils.RemoteFileStream remoteFile = DownloadUtils.openRemoteFile(picture.getUrl(), MAX_SHARE_PROXY_SIZE);
        recordShareAccess(share);
        return new ShareFileVO(
                picture.getPictureName(),
                remoteFile.getContentType(),
                remoteFile.getContentLength(),
                remoteFile.getInputStream(),
                remoteFile);
    }

    private record ShareResolved(PictureShare share, String tokenHash) {
    }
}
