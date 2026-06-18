package hk.ljx.fishpicsbackend.picture.component;

import cn.hutool.core.util.StrUtil;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class MultipartUploadCleaner {

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private MultipartUploadSessionStore uploadSessionStore;

    @Resource
    private MultipartUploadCoordinator uploadCoordinator;

    public PictureVO resolveMergeResultIfAvailable(Map<String, Object> mergeResult, String md5, Long userId) {
        if (mergeResult == null) {
            return null;
        }
        validateMergeResultOwner(mergeResult, userId);
        String mergedCosKey = getMergeResultValue(mergeResult, "cosKey");
        if (StrUtil.isNotBlank(mergedCosKey) && uploadCoordinator.isMergedObjectAvailable(mergedCosKey)) {
            PictureVO result = buildPictureVOFromMergeResult(mergeResult);
            uploadSessionStore.cleanup(md5, true, userId);
            return result;
        }
        String pictureId = getMergeResultValue(mergeResult, "pictureId");
        if (StrUtil.isNotBlank(pictureId)) {
            Picture existingPicture = pictureMapper.selectById(Long.parseLong(pictureId));
            if (existingPicture != null) {
                uploadSessionStore.restoreMergeResult(md5, userId, existingPicture, mergedCosKey);
                PictureVO result = buildPictureVOFromMergeResult(mergeResult);
                uploadSessionStore.cleanup(md5, true, userId);
                return result;
            }
        }
        return null;
    }

    public void saveMergeResultQuietly(String mergeResultKey, Picture picture, String cosKey, Long userId, long size) {
        try {
            uploadSessionStore.saveMergeResult(mergeResultKey, picture, cosKey, userId, size);
        } catch (Exception e) {
            log.warn("mergeChunks write merge result failed: pictureId={}, err={}", picture.getId(), e.getMessage());
        }
    }

    private void validateMergeResultOwner(Map<String, Object> mergeResult, Long userId) {
        String storedUserId = getMergeResultValue(mergeResult, "userId");
        ExcUtils.throwIfTrue(StrUtil.isBlank(storedUserId) || !storedUserId.equals(String.valueOf(userId)),
                ExceptionCode.FORBIDDEN, "upload session owner mismatch");
    }

    private PictureVO buildPictureVOFromMergeResult(Map<String, Object> mergeResult) {
        String pictureId = getMergeResultValue(mergeResult, "pictureId");
        ExcUtils.throwIfTrue(StrUtil.isBlank(pictureId), ExceptionCode.PARAMETER_ERROR, "invalid merge result");
        return PictureVO.ofUpload(Long.parseLong(pictureId), getMergeResultValue(mergeResult, "url"));
    }

    private String getMergeResultValue(Map<String, Object> mergeResult, String key) {
        Object value = mergeResult.get(key);
        return value == null ? null : value.toString();
    }
}
