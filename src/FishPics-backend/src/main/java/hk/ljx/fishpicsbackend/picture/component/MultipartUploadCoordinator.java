package hk.ljx.fishpicsbackend.picture.component;

import com.qcloud.cos.model.PartETag;
import cn.hutool.core.util.StrUtil;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.infra.CosService;
import hk.ljx.fishpicsbackend.common.infra.RedisAtomicOps;
import hk.ljx.fishpicsbackend.common.utils.FileTypeUtils;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.picture.dto.PictureMetadata;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.FileResourceService;
import hk.ljx.fishpicsbackend.space.entity.Space;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@Component
public class MultipartUploadCoordinator {

    @Resource
    private CosService cosService;

    @Resource
    private RedisAtomicOps redisAtomicOps;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private FileResourceService fileResourceService;

    @Resource
    private MultipartUploadSessionStore uploadSessionStore;

    @Resource
    private SpaceQuotaManager quotaManager;

    @Resource
    private PlatformTransactionManager transactionManager;

    public String getOrCreateUploadId(Long userId, String md5, String cosKey) {
        String uploadId = uploadSessionStore.getUploadId(userId, md5);
        if (StrUtil.isNotBlank(uploadId)) {
            return uploadId;
        }
        String newUploadId = cosService.initiateMultipartUpload(cosKey);
        uploadId = redisAtomicOps.setIfAbsentOrGet(
                uploadSessionStore.uploadIdKey(userId, md5),
                newUploadId,
                RedisConstants.FILE_UPLOAD_TTL * 3600);
        if (!newUploadId.equals(uploadId)) {
            log.warn("[uploadChunk] uploadId race: cosKey={}, mine={}, actual={}", cosKey, newUploadId, uploadId);
            // 竞争失败，abort 泄漏的 COS 分片上传
            cosService.abortMultipartUpload(cosKey, newUploadId);
        }
        return uploadId;
    }

    public String uploadPart(MultipartFile file, String cosKey, String uploadId, Integer chunkIndex) {
        try (InputStream inputStream = file.getInputStream()) {
            InputStream uploadStream = inputStream;
            if (chunkIndex == 0) {
                byte[] header = new byte[16];
                int read = inputStream.read(header);
                ExcUtils.throwIfTrue(read <= 0, ExceptionCode.PARAMETER_ERROR, "empty first chunk");
                String fileType = FileTypeUtils.getValidFileType(
                        new java.io.ByteArrayInputStream(header, 0, read));
                ExcUtils.throwIfTrue(fileType == null, ExceptionCode.PARAMETER_ERROR, "不支持的图片格式");
                uploadStream = new java.io.SequenceInputStream(
                        new java.io.ByteArrayInputStream(header, 0, Math.max(read, 0)), inputStream);
            }
            return cosService.uploadPart(cosKey, uploadId, chunkIndex + 1, uploadStream, file.getSize());
        } catch (IOException e) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "分片上传失败");
        }
    }

    public boolean isMergedObjectAvailable(String cosKey) {
        try {
            cosService.getObjectContentType(cosKey);
            return true;
        } catch (BaseException e) {
            return false;
        }
    }

    public void registerMultipartMergeAfterCommit(MultipartMergeContext context) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                boolean mergeOk = tryCompleteMultipartUpload(
                        context.cosKey(),
                        context.uploadId(),
                        context.partETags(),
                        context.md5(),
                        context.mergeResultKey(),
                        context.userId());
                if (!mergeOk) {
                    handleMultipartMergeFailure(context);
                    return;
                }
                updateMergedPictureMetadata(context.pictureId(), context.cosKey());
            }
        });
    }

    private boolean tryCompleteMultipartUpload(String cosKey, String uploadId, List<PartETag> partETags,
                                               String md5, String mergeResultKey, Long userId) {
        try {
            cosService.completeMultipartUpload(cosKey, uploadId, partETags);
            log.info("COS multipart merge completed: cosKey={}", cosKey);
            uploadSessionStore.cleanup(md5, true, userId);
            return true;
        } catch (Exception e) {
            if (isMergedObjectAvailable(cosKey)) {
                log.warn("COS merge returned an error but object is already available: cosKey={}", cosKey, e);
                uploadSessionStore.cleanup(md5, true, userId);
                return true;
            }
            cosService.abortMultipartUpload(cosKey, uploadId);
            uploadSessionStore.keepMergeResult(mergeResultKey);
            log.error("COS multipart merge failed: cosKey={}, md5={}, error={}", cosKey, md5, e.getMessage());
            return false;
        }
    }

    private void handleMultipartMergeFailure(MultipartMergeContext context) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> doHandleMultipartMergeFailure(context));
    }

    private void doHandleMultipartMergeFailure(MultipartMergeContext context) {
        try {
            pictureMapper.deleteById(context.pictureId());
            log.error("[mergeChunks] COS merge failed, DB picture deleted: pictureId={}, cosKey={}",
                    context.pictureId(), context.cosKey());
            quotaManager.release(context.space(), context.size());
            if (context.resourceId() != null) {
                fileResourceService.decrementRefCount(context.resourceId());
            }
            uploadSessionStore.cleanup(context.md5(), true, context.userId());
        } catch (Exception ex) {
            log.error("[mergeChunks] cleanup after COS merge failure partially failed: pictureId={}, cosKey={}",
                    context.pictureId(), context.cosKey(), ex);
        }
    }

    private void updateMergedPictureMetadata(Long pictureId, String cosKey) {
        try {
            PictureMetadata metadata = cosService.getPictureMetadata(cosKey);
            if (metadata != null && (metadata.getWidth() != null || metadata.getHeight() != null)) {
                Picture update = new Picture();
                update.setId(pictureId);
                update.setWidth(metadata.getWidth());
                update.setHeight(metadata.getHeight());
                pictureMapper.updateById(update);
            }
        } catch (Exception e) {
            log.warn("failed to read metadata after multipart merge: cosKey={}", cosKey, e);
        }
    }

    public record MultipartMergeContext(Long pictureId,
                                        Long resourceId,
                                        String cosKey,
                                        String uploadId,
                                        List<PartETag> partETags,
                                        String md5,
                                        String mergeResultKey,
                                        Long userId,
                                        Space space,
                                        long size) {
    }
}
