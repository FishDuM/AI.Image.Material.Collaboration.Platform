package hk.ljx.fishpicsbackend.picture.component;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qcloud.cos.model.PartETag;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.infra.CosService;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.picture.dto.CheckUploadRequest;
import hk.ljx.fishpicsbackend.picture.dto.MergeChunksRequest;
import hk.ljx.fishpicsbackend.picture.entity.FileResource;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.FileResourceService;
import hk.ljx.fishpicsbackend.picture.vo.CheckUploadVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import hk.ljx.fishpicsbackend.picture.vo.UploadChunkVO;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class MultipartUploadManager {

    private static final long MAX_CHUNK_SIZE = 5L * 1024 * 1024;
    private static final int MAX_CHUNK_COUNT = 6000;

    @Resource
    private CosService cosService;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private FileResourceService fileResourceService;

    @Resource
    private MultipartUploadSessionStore uploadSessionStore;

    @Resource
    private SpaceQuotaManager quotaManager;

    @Resource
    private SpaceWritePermissionChecker spaceWritePermissionChecker;

    @Resource
    private MultipartUploadCoordinator uploadCoordinator;

    @Resource
    private MultipartUploadCleaner uploadCleaner;

    @Resource
    private MultipartUploadSupport uploadSupport;

    @Transactional(rollbackFor = Exception.class)
    public CheckUploadVO checkUpload(CheckUploadRequest request) {
        uploadSupport.validateMd5(request.getMd5());
        ExcUtils.throwIfTrue(request.getSize() == null, "文件大小不能为空");

        User user = LoginContextHelper.requireUser();
        uploadSessionStore.bindOwner(request.getMd5(), user.getId());

        long maxSize = uploadSupport.getMaxUploadSize(user.getLevel());
        ExcUtils.throwIfTrue(request.getSize() > maxSize,
                ExceptionCode.PARAMETER_ERROR, "文件大小超过限制（最大" + uploadSupport.formatSize(maxSize) + "）");

        FileResource resource = fileResourceService.findByMd5AndSize(request.getMd5(), request.getSize());
        if (resource != null) {
            return handleDuplicateUpload(request, user, resource);
        }

        Long chunkCount = uploadSessionStore.uploadedChunkCount(user.getId(), request.getMd5());
        if (chunkCount != null && chunkCount > 0) {
            return resumeUpload(request, user);
        }

        return startNewUpload(request, user);
    }

    public UploadChunkVO uploadChunk(MultipartFile file, String md5, Integer chunkIndex) {
        uploadSupport.validateMd5(md5);
        ExcUtils.throwIfTrue(file == null || file.isEmpty(), "分片文件不能为空");
        ExcUtils.throwIfTrue(chunkIndex == null || chunkIndex < 0, "分片编号无效");
        ExcUtils.throwIfTrue(file.getSize() > MAX_CHUNK_SIZE,
                ExceptionCode.PARAMETER_ERROR, "单个分片不能超过" + uploadSupport.formatSize(MAX_CHUNK_SIZE));
        ExcUtils.throwIfTrue(chunkIndex >= MAX_CHUNK_COUNT,
                ExceptionCode.PARAMETER_ERROR, "分片数量超过限制");

        User user = LoginContextHelper.requireUser();
        uploadSessionStore.validateOwner(md5, user.getId());

        String cosKey = uploadSessionStore.getRequiredCosKey(user.getId(), md5);
        if (uploadSessionStore.isChunkUploaded(user.getId(), md5, chunkIndex)) {
            String existingEtag = uploadSessionStore.getChunkEtag(user.getId(), md5, chunkIndex);
            uploadSessionStore.refreshTtl(md5, user.getId());
            return new UploadChunkVO(existingEtag, chunkIndex);
        }

        String uploadId = uploadCoordinator.getOrCreateUploadId(user.getId(), md5, cosKey);
        String etag = uploadCoordinator.uploadPart(file, cosKey, uploadId, chunkIndex);
        uploadSessionStore.saveChunk(user.getId(), md5, chunkIndex, etag, file.getSize());
        return new UploadChunkVO(etag, chunkIndex);
    }

    private CheckUploadVO handleDuplicateUpload(CheckUploadRequest request, User user, FileResource resource) {
        Space space = uploadSupport.resolveTargetSpace(request.getTargetSpaceId(), user.getId());
        Picture existingPicture = pictureMapper.selectOne(new LambdaQueryWrapper<Picture>()
                .eq(Picture::getResourceId, resource.getId())
                .eq(Picture::getUserId, user.getId())
                .eq(Picture::getSpaceId, space.getId())
                .last("LIMIT 1"));
        if (existingPicture != null) {
            return CheckUploadVO.builder()
                    .status("duplicate")
                    .picture(PictureVO.ofUpload(existingPicture.getId(), existingPicture.getUrl()))
                    .build();
        }

        int refResult = fileResourceService.incrementRefCount(resource.getId());
        ExcUtils.throwIfTrue(refResult == -1, ExceptionCode.DATABASE_ERROR, "文件资源不存在，请重新上传");
        Picture picture = uploadSupport.createPictureFromResource(resource, user.getId(), space);
        return CheckUploadVO.builder()
                .status("duplicate")
                .picture(PictureVO.ofUpload(picture.getId(), picture.getUrl()))
                .build();
    }

    private CheckUploadVO resumeUpload(CheckUploadRequest request, User user) {
        String md5 = request.getMd5();
        Set<String> uploadedChunks = uploadSessionStore.uploadedChunks(user.getId(), md5);
        String cosKey = uploadSessionStore.getCosKey(user.getId(), md5);
        if (StrUtil.isBlank(cosKey)) {
            cosKey = cosService.generateKey();
            uploadSessionStore.saveCosKey(user.getId(), md5, cosKey);
        }

        uploadSessionStore.refreshTtl(md5, user.getId());
        return CheckUploadVO.builder()
                .status("resume")
                .uploadedChunks(uploadedChunks != null
                        ? uploadedChunks.stream().map(Integer::parseInt).sorted().toList()
                        : List.of())
                .uploadId(uploadSessionStore.getUploadId(user.getId(), md5))
                .cosKey(cosKey)
                .build();
    }

    private CheckUploadVO startNewUpload(CheckUploadRequest request, User user) {
        String cosKey = cosService.generateKey();
        uploadSessionStore.saveCosKey(user.getId(), request.getMd5(), cosKey);
        uploadSessionStore.clearMergeResult(request.getMd5(), user.getId());
        uploadSessionStore.refreshTtl(request.getMd5(), user.getId());
        return CheckUploadVO.builder()
                .status("new")
                .cosKey(cosKey)
                .build();
    }

    @Transactional(rollbackFor = Exception.class)
    public PictureVO mergeChunks(MergeChunksRequest request) {
        uploadSupport.validateMergeRequest(request);

        User user = LoginContextHelper.requireUser();
        long maxSize = uploadSupport.getMaxUploadSize(user.getLevel());

        Map<String, Object> mergeResult = uploadSessionStore.getMergeResult(request.getMd5(), user.getId());
        PictureVO idempotentResult = uploadCleaner.resolveMergeResultIfAvailable(mergeResult, request.getMd5(), user.getId());
        if (idempotentResult != null) {
            return idempotentResult;
        }

        uploadSessionStore.validateOwner(request.getMd5(), user.getId());
        ExcUtils.throwIfTrue(request.getSize() > maxSize,
                ExceptionCode.PARAMETER_ERROR, "文件大小超过限制（最大" + uploadSupport.formatSize(maxSize) + "）");
        validateUploadedChunks(user.getId(), request);

        String uploadId = uploadSessionStore.getUploadId(user.getId(), request.getMd5());
        ExcUtils.throwIfTrue(StrUtil.isBlank(uploadId), ExceptionCode.PARAMETER_ERROR, "uploadId 不存在");
        List<PartETag> partETags = uploadSessionStore.loadPartETags(
                user.getId(), request.getMd5(), request.getTotalChunks());

        Space space = uploadSupport.resolveTargetSpace(request.getTargetSpaceId(), user.getId());
        spaceWritePermissionChecker.check(space, user.getId());
        long size = uploadSessionStore.sumUploadedChunkSizes(
                user.getId(), request.getMd5(), request.getTotalChunks());
        ExcUtils.throwIfTrue(size > maxSize, ExceptionCode.PARAMETER_ERROR, "file size exceeds limit");
        ExcUtils.throwIfFalse(mergeResult != null || quotaManager.reserve(space, size),
                ExceptionCode.PARAMETER_ERROR, "空间容量不足");

        String cosKey = uploadSessionStore.getRequiredCosKey(user.getId(), request.getMd5());
        String mergeResultKey = uploadSessionStore.mergeResultKey(request.getMd5(), user.getId());
        Picture picture = uploadSupport.createMultipartPicture(request.getMd5(), size, cosKey, user.getId(), space);
        uploadCleaner.saveMergeResultQuietly(mergeResultKey, picture, cosKey, user.getId(), size);

        uploadCoordinator.registerMultipartMergeAfterCommit(new MultipartUploadCoordinator.MultipartMergeContext(
                picture.getId(), picture.getResourceId(), cosKey, uploadId, partETags,
                request.getMd5(), mergeResultKey, user.getId(), space, size));
        return PictureVO.ofUpload(picture.getId(), picture.getUrl());
    }

    private void validateUploadedChunks(Long userId, MergeChunksRequest request) {
        Long uploadedCount = uploadSessionStore.uploadedChunkCount(userId, request.getMd5());
        ExcUtils.throwIfTrue(uploadedCount == null || uploadedCount != request.getTotalChunks().longValue(),
                ExceptionCode.PARAMETER_ERROR,
                "分片不完整，已上传" + uploadedCount + "/" + request.getTotalChunks());
    }

}
