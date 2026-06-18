package hk.ljx.fishpicsbackend.picture.component;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.qcloud.cos.model.PartETag;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class MultipartUploadSessionStore {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public void bindOwner(String md5, Long userId) {
        stringRedisTemplate.opsForValue().set(
                RedisConstants.getUserFileUploadOwnerKey(userId, md5),
                String.valueOf(userId),
                RedisConstants.FILE_UPLOAD_TTL,
                TimeUnit.HOURS);
    }

    public void validateOwner(String md5, Long userId) {
        String owner = stringRedisTemplate.opsForValue()
                .get(RedisConstants.getUserFileUploadOwnerKey(userId, md5));
        ExcUtils.throwIfTrue(StrUtil.isBlank(owner),
                ExceptionCode.PARAMETER_ERROR, "上传会话已过期，请重新上传");
        ExcUtils.throwIfTrue(!owner.equals(String.valueOf(userId)),
                ExceptionCode.FORBIDDEN, "该上传会话不属于当前用户");
    }

    public Long uploadedChunkCount(Long userId, String md5) {
        return stringRedisTemplate.opsForSet().size(RedisConstants.getFileUploadChunksKey(userId, md5));
    }

    public Set<String> uploadedChunks(Long userId, String md5) {
        return stringRedisTemplate.opsForSet().members(RedisConstants.getFileUploadChunksKey(userId, md5));
    }

    public boolean isChunkUploaded(Long userId, String md5, Integer chunkIndex) {
        Boolean uploaded = stringRedisTemplate.opsForSet()
                .isMember(RedisConstants.getFileUploadChunksKey(userId, md5), String.valueOf(chunkIndex));
        return Boolean.TRUE.equals(uploaded);
    }

    public String getChunkEtag(Long userId, String md5, Integer chunkIndex) {
        Object etag = stringRedisTemplate.opsForHash()
                .get(RedisConstants.getFileChunkEtagKey(userId, md5), String.valueOf(chunkIndex));
        return etag == null ? "" : etag.toString();
    }

    public void saveChunk(Long userId, String md5, Integer chunkIndex, String etag, long size) {
        String chunkIndexValue = String.valueOf(chunkIndex);
        stringRedisTemplate.opsForSet().add(RedisConstants.getFileUploadChunksKey(userId, md5), chunkIndexValue);
        stringRedisTemplate.opsForHash().put(RedisConstants.getFileChunkEtagKey(userId, md5), chunkIndexValue, etag);
        stringRedisTemplate.opsForHash().put(RedisConstants.getFileChunkSizeKey(userId, md5), chunkIndexValue, String.valueOf(size));
        refreshTtl(md5, userId);
    }

    public String getUploadId(Long userId, String md5) {
        return stringRedisTemplate.opsForValue().get(RedisConstants.getFileUploadIdKey(userId, md5));
    }

    public String uploadIdKey(Long userId, String md5) {
        return RedisConstants.getFileUploadIdKey(userId, md5);
    }

    public String getCosKey(Long userId, String md5) {
        return stringRedisTemplate.opsForValue().get(RedisConstants.getFileCosKeyKey(userId, md5));
    }

    public String getRequiredCosKey(Long userId, String md5) {
        String cosKey = getCosKey(userId, md5);
        ExcUtils.throwIfTrue(StrUtil.isBlank(cosKey), ExceptionCode.PARAMETER_ERROR, "上传会话已过期，请重新上传");
        return cosKey;
    }

    public void saveCosKey(Long userId, String md5, String cosKey) {
        stringRedisTemplate.opsForValue().set(
                RedisConstants.getFileCosKeyKey(userId, md5),
                cosKey,
                RedisConstants.FILE_UPLOAD_TTL,
                TimeUnit.HOURS);
    }

    public void clearMergeResult(String md5, Long userId) {
        stringRedisTemplate.delete(RedisConstants.getFileMergeResultKey(userId, md5));
    }

    public Map<String, Object> getMergeResult(String md5, Long userId) {
        String raw = stringRedisTemplate.opsForValue().get(RedisConstants.getFileMergeResultKey(userId, md5));
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        return JSONUtil.toBean(raw, Map.class);
    }

    public void restoreMergeResult(String md5, Long userId, Picture picture, String cosKey) {
        Map<String, String> data = new HashMap<>();
        data.put("pictureId", String.valueOf(picture.getId()));
        data.put("url", picture.getUrl());
        data.put("userId", String.valueOf(picture.getUserId()));
        data.put("cosKey", cosKey != null ? cosKey : "");
        data.put("size", String.valueOf(picture.getSize()));
        stringRedisTemplate.opsForValue().set(
                RedisConstants.getFileMergeResultKey(userId, md5),
                JSONUtil.toJsonStr(data),
                RedisConstants.FILE_UPLOAD_TTL,
                TimeUnit.HOURS);
    }

    public void saveMergeResult(String mergeResultKey, Picture picture, String cosKey, Long userId, long size) {
        Map<String, String> data = new HashMap<>();
        data.put("pictureId", String.valueOf(picture.getId()));
        data.put("url", picture.getUrl());
        data.put("userId", String.valueOf(userId));
        data.put("cosKey", cosKey);
        data.put("size", String.valueOf(size));
        stringRedisTemplate.opsForValue().set(
                mergeResultKey,
                JSONUtil.toJsonStr(data),
                RedisConstants.FILE_UPLOAD_TTL,
                TimeUnit.HOURS);
    }

    public String mergeResultKey(String md5, Long userId) {
        return RedisConstants.getFileMergeResultKey(userId, md5);
    }

    public void keepMergeResult(String mergeResultKey) {
        stringRedisTemplate.expire(mergeResultKey, RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
    }

    public long sumUploadedChunkSizes(Long userId, String md5, Integer totalChunks) {
        Map<Object, Object> chunkSizeMap = stringRedisTemplate.opsForHash()
                .entries(RedisConstants.getFileChunkSizeKey(userId, md5));
        ExcUtils.throwIfTrue(chunkSizeMap.size() != totalChunks,
                ExceptionCode.PARAMETER_ERROR, "chunk size data is incomplete");
        long totalSize = 0L;
        for (Object value : chunkSizeMap.values()) {
            ExcUtils.throwIfTrue(value == null, ExceptionCode.PARAMETER_ERROR, "invalid chunk size");
            totalSize += Long.parseLong(value.toString());
        }
        return totalSize;
    }

    public List<PartETag> loadPartETags(Long userId, String md5, Integer totalChunks) {
        Map<Object, Object> etagMap = stringRedisTemplate.opsForHash()
                .entries(RedisConstants.getFileChunkEtagKey(userId, md5));
        ExcUtils.throwIfTrue(etagMap.size() != totalChunks,
                ExceptionCode.PARAMETER_ERROR, "分片 ETag 不完整，请重试合并");
        return etagMap.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> Integer.parseInt(entry.getKey().toString())))
                .map(entry -> new PartETag(
                        Integer.parseInt(entry.getKey().toString()) + 1,
                        entry.getValue().toString()))
                .collect(Collectors.toList());
    }

    public void refreshTtl(String md5, Long userId) {
        stringRedisTemplate.expire(RedisConstants.getUserFileUploadOwnerKey(userId, md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        stringRedisTemplate.expire(RedisConstants.getFileCosKeyKey(userId, md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        stringRedisTemplate.expire(RedisConstants.getFileUploadIdKey(userId, md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        stringRedisTemplate.expire(RedisConstants.getFileUploadChunksKey(userId, md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        stringRedisTemplate.expire(RedisConstants.getFileChunkEtagKey(userId, md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        stringRedisTemplate.expire(RedisConstants.getFileChunkSizeKey(userId, md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        stringRedisTemplate.expire(RedisConstants.getFileMergeResultKey(userId, md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
    }

    public void cleanup(String md5, boolean deleteMergeResult, Long userId) {
        Long uid = userId != null ? userId : 0L;
        stringRedisTemplate.delete(RedisConstants.getFileUploadChunksKey(uid, md5));
        stringRedisTemplate.delete(RedisConstants.getFileUploadIdKey(uid, md5));
        stringRedisTemplate.delete(RedisConstants.getFileChunkEtagKey(uid, md5));
        stringRedisTemplate.delete(RedisConstants.getFileChunkSizeKey(uid, md5));
        stringRedisTemplate.delete(RedisConstants.getFileCosKeyKey(uid, md5));
        if (userId != null) {
            stringRedisTemplate.delete(RedisConstants.getUserFileUploadOwnerKey(userId, md5));
        }
        if (deleteMergeResult) {
            stringRedisTemplate.delete(RedisConstants.getFileMergeResultKey(uid, md5));
        }
    }
}
