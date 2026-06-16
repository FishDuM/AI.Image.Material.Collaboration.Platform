package hk.ljx.fishpicsbackend.picture.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.utils.DownloadUtils;
import hk.ljx.fishpicsbackend.common.utils.IpUtils;
import hk.ljx.fishpicsbackend.common.utils.RedisAtomicOps;
import hk.ljx.fishpicsbackend.mapper.PictureShareItemMapper;
import hk.ljx.fishpicsbackend.mapper.PictureShareMapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.entity.PictureShare;
import hk.ljx.fishpicsbackend.picture.entity.PictureShareItem;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.picture.service.ShareService;
import hk.ljx.fishpicsbackend.picture.vo.ShareFileVO;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShareServiceImpl implements ShareService {

    /** 分享代理最大文件大小：50MB，防止资源耗尽 */
    private static final long MAX_SHARE_PROXY_SIZE = 50L * 1024 * 1024;

    /** 分享 token 频控 — 同一 token 5 次/分钟 */
    private static final long SHARE_VIEW_RATE_LIMIT_PER_MIN = 5;
    private static final long SHARE_VIEW_RATE_WINDOW_SEC = 60;

    /** 分享默认最大访问次数 */
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
    public String createShare(List<Long> pictureIds, Long userId, int expireDays, int allowDownload, Integer maxViewCount) {
        ExcUtils.throwIfTrue(pictureIds == null || pictureIds.isEmpty(), ExceptionCode.PARAMETER_ERROR, "至少选择一张图片");

        // 校验所有图片都存在且属于当前用户
        List<Picture> pictures = pictureService.listByIds(pictureIds);
        ExcUtils.throwIfTrue(pictures.size() != pictureIds.size(), ExceptionCode.NOT_FOUND, "部分图片不存在");
        for (Picture picture : pictures) {
            ExcUtils.throwIfTrue(!picture.getUserId().equals(userId), ExceptionCode.FORBIDDEN, "只能分享自己的图片");
        }

        int actualExpireDays = Math.min(Math.max(expireDays, 1), 7);
        int actualMaxView = (maxViewCount == null || maxViewCount <= 0)
                ? DEFAULT_MAX_VIEW_COUNT
                : Math.min(maxViewCount, 1000);
        String shareToken = generateSecureToken();
        Date expireTime = new Date(System.currentTimeMillis() + actualExpireDays * 86400_000L);

        PictureShare share = new PictureShare();
        share.setPictureId(pictureIds.get(0)); // 保留第一个图片ID用于向后兼容
        share.setShareToken(shareToken); // 明文 token（仅创建时返回）
        share.setShareTokenHash(hashShareToken(shareToken));
        share.setShareUserId(userId);
        share.setExpireTime(expireTime);
        share.setAllowDownload(allowDownload == 1 ? 1 : 0);
        share.setStatus(1);
        share.setMaxViewCount(actualMaxView);
        share.setCreateTime(new Date());
        pictureShareMapper.insert(share);

        // 写入分享-图片关联记录
        for (int i = 0; i < pictureIds.size(); i++) {
            PictureShareItem item = new PictureShareItem();
            item.setShareId(share.getId());
            item.setPictureId(pictureIds.get(i));
            item.setSortOrder(i);
            pictureShareItemMapper.insert(item);
        }

        log.info("create share pictureIds={}, userId={}, token={}****, count={}, maxView={}",
                pictureIds, userId, shareToken.substring(0, Math.min(8, shareToken.length())), pictureIds.size(), actualMaxView);
        return shareToken;
    }

    /**
     * 256bit SecureRandom 随机 token
     */
    private String generateSecureToken() {
        return cn.hutool.core.util.RandomUtil.randomString(43);
    }

    /**
     * token 哈希（用于数据库存储和查询）
     */
    private String hashShareToken(String token) {
        if (token == null) return null;
        return DigestUtil.sha256Hex(token);
    }

    @Override
    public Map<String, Object> getShareInfo(String shareToken) {
        ShareResolved resolved = resolveShare(shareToken, false);
        PictureShare share = resolved.share();
        List<Picture> pictures = resolvePictures(share);

        List<Map<String, Object>> pictureList = new ArrayList<>(pictures.size());
        for (Picture picture : pictures) {
            Map<String, Object> picInfo = new LinkedHashMap<>();
            picInfo.put("pictureId", picture.getId());
            picInfo.put("pictureName", picture.getPictureName());
            picInfo.put("introduction", picture.getIntroduction());
            picInfo.put("width", picture.getWidth());
            picInfo.put("height", picture.getHeight());
            picInfo.put("previewUrl", "/api/share/preview/" + shareToken + "?pictureId=" + picture.getId());
            picInfo.put("downloadUrl", Integer.valueOf(1).equals(share.getAllowDownload())
                    ? "/api/share/download/" + shareToken + "?pictureId=" + picture.getId() : null);
            pictureList.add(picInfo);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expireTime", share.getExpireTime());
        result.put("allowDownload", share.getAllowDownload());
        result.put("pictures", pictureList);
        return result;
    }

    @Override
    public ShareFileVO getPreviewFile(String shareToken, Long pictureId) {
        ShareResolved resolved = resolveShare(shareToken, true);
        Picture picture = resolveTargetPicture(resolved.share(), pictureId);
        return buildShareFile(resolved.share(), picture);
    }

    @Override
    public ShareFileVO getDownloadFile(String shareToken, Long pictureId) {
        ShareResolved resolved = resolveShare(shareToken, true);
        ExcUtils.throwIfTrue(!Integer.valueOf(1).equals(resolved.share().getAllowDownload()),
                ExceptionCode.FORBIDDEN, "当前分享不允许下载");
        Picture picture = resolveTargetPicture(resolved.share(), pictureId);
        return buildShareFile(resolved.share(), picture);
    }

    /**
     * 根据分享和图片ID解析目标图片
     */
    private Picture resolveTargetPicture(PictureShare share, Long pictureId) {
        if (pictureId != null) {
            // 校验 pictureId 属于该分享
            List<PictureShareItem> items = pictureShareItemMapper.selectList(
                    new LambdaQueryWrapper<PictureShareItem>()
                            .eq(PictureShareItem::getShareId, share.getId())
                            .eq(PictureShareItem::getPictureId, pictureId)
                            .last("LIMIT 1"));
            ExcUtils.throwIfTrue(items.isEmpty(), ExceptionCode.NOT_FOUND, "该图片不属于此分享");
            Picture picture = pictureService.getById(pictureId);
            ExcUtils.throwIfTrue(picture == null, ExceptionCode.NOT_FOUND, "图片已被删除");
            return picture;
        }
        // 没有传 pictureId 时，获取分享中的第一张图（向后兼容）
        List<Picture> pictures = resolvePictures(share);
        ExcUtils.throwIfTrue(pictures.isEmpty(), ExceptionCode.NOT_FOUND, "分享中没有图片");
        return pictures.get(0);
    }

    /**
     * 获取分享关联的所有图片，按 sort_order 排序
     */
    private List<Picture> resolvePictures(PictureShare share) {
        List<PictureShareItem> items = pictureShareItemMapper.selectList(
                new LambdaQueryWrapper<PictureShareItem>()
                        .eq(PictureShareItem::getShareId, share.getId())
                        .orderByAsc(PictureShareItem::getSortOrder));
        if (items.isEmpty()) {
            // 向后兼容：没有关联表记录时，使用 picture_share.picture_id
            Picture picture = pictureService.getById(share.getPictureId());
            if (picture != null) {
                return List.of(picture);
            }
            return List.of();
        }
        List<Long> pictureIds = items.stream().map(PictureShareItem::getPictureId).collect(Collectors.toList());
        // 保持 sort_order 顺序
        List<Picture> pictures = pictureService.listByIds(pictureIds);
        Map<Long, Picture> picMap = pictures.stream().collect(Collectors.toMap(Picture::getId, p -> p));
        List<Picture> ordered = new ArrayList<>(pictureIds.size());
        for (Long pid : pictureIds) {
            Picture p = picMap.get(pid);
            if (p != null) ordered.add(p);
        }
        return ordered;
    }

    /**
     * 把 viewCount 计数 + maxViewCount 检查合并到原子操作中
     */
    private long incrementViewCount(String tokenHash, Date expireTime, int maxViewCount) {
        String viewCountKey = "SHARE:VIEW:COUNT:" + tokenHash;
        long ttlSec;
        if (expireTime != null) {
            long remainingMs = Math.max(expireTime.getTime() - System.currentTimeMillis() + 3600_000L, 60_000L);
            ttlSec = remainingMs / 1000L;
        } else {
            ttlSec = 86400L;
        }
        return redisAtomicOps.incrWithCheckAndRollback(viewCountKey, ttlSec, maxViewCount);
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
        try {
            stringRedisTemplate.delete("SHARE:VIEW:COUNT:" + share.getShareTokenHash());
            stringRedisTemplate.delete("SHARE:VIEW:RATE:" + share.getShareTokenHash());
        } catch (Exception e) {
            log.warn("cancelShare 清理 Redis 计数 key 失败(可接受,会自然过期): shareId={}, error={}",
                    shareId, e.getMessage());
        }
        log.info("cancel share shareId={}, userId={}", shareId, userId);
    }

    private ShareResolved resolveShare(String shareToken) {
        return resolveShare(shareToken, false);
    }

    private ShareResolved resolveShare(String shareToken, boolean countView) {
        String tokenHash = hashShareToken(shareToken);
        PictureShare share = pictureShareMapper.selectOne(new LambdaQueryWrapper<PictureShare>()
                .eq(PictureShare::getShareTokenHash, tokenHash)
                .eq(PictureShare::getStatus, 1)
                .last("LIMIT 1"));
        ExcUtils.throwIfTrue(share == null, ExceptionCode.NOT_FOUND, "分享链接不存在或已失效");
        ExcUtils.throwIfTrue(share.getExpireTime() == null || share.getExpireTime().before(new Date()),
                ExceptionCode.FORBIDDEN, "分享链接已过期");

        String rateKey = "SHARE:VIEW:RATE:" + tokenHash;
        long currentCount = redisAtomicOps.incrWithExpire(rateKey, SHARE_VIEW_RATE_WINDOW_SEC);
        ExcUtils.throwIfTrue(currentCount > SHARE_VIEW_RATE_LIMIT_PER_MIN,
                ExceptionCode.TOO_MANY_REQUESTS, "访问过于频繁,请稍后再试");

        if (countView) {
            Integer maxView = share.getMaxViewCount() != null ? share.getMaxViewCount() : DEFAULT_MAX_VIEW_COUNT;
            long newView = incrementViewCount(tokenHash, share.getExpireTime(), maxView);
            if (newView == -1L) {
                share.setStatus(0);
                try {
                    pictureShareMapper.updateById(share);
                } catch (Exception dbEx) {
                    log.error("share auto-cancel DB update failed, share will revive after Redis TTL: shareId={}", share.getId(), dbEx);
                }
                log.info("share auto-cancelled (exceed maxView) shareId={}, token={}****, max={}",
                        share.getId(), shareToken.substring(0, Math.min(8, shareToken.length())), maxView);
                throw new BaseException(ExceptionCode.FORBIDDEN, "分享链接已达最大访问次数");
            }
            log.debug("share view+1: shareId={}, token={}****, views={}/{}",
                    share.getId(), shareToken.substring(0, Math.min(8, shareToken.length())), newView, maxView);
        }

        return new ShareResolved(share);
    }

    private void recordShareAccess(PictureShare share, Picture picture) {
        try {
            ServletRequestAttributes attr = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attr == null) return;
            String ip = IpUtils.getClientIp(attr.getRequest());
            String ua = attr.getRequest().getHeader("User-Agent");
            if (ua != null && ua.length() > 256) ua = ua.substring(0, 256);
            java.util.Map<String, Object> logEntry = new LinkedHashMap<>();
            logEntry.put("ts", System.currentTimeMillis());
            logEntry.put("ip", ip);
            logEntry.put("ua", ua);
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
        recordShareAccess(share, picture);
        return new ShareFileVO(
                picture.getPictureName(),
                remoteFile.getContentType(),
                remoteFile.getContentLength(),
                remoteFile.getInputStream(),
                remoteFile
        );
    }

    private record ShareResolved(PictureShare share) {
    }
}
