package hk.ljx.fishpicsbackend.picture.component;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qcloud.cos.model.PartETag;
import hk.ljx.fishpicsbackend.common.cache.RedisCacheManager;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.infra.CosService;
import hk.ljx.fishpicsbackend.common.infra.RedisAtomicOps;
import hk.ljx.fishpicsbackend.common.utils.DownloadUtils;
import hk.ljx.fishpicsbackend.common.utils.FileTypeUtils;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.picture.dto.CheckUploadRequest;
import hk.ljx.fishpicsbackend.picture.dto.MergeChunksRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureMetadata;
import hk.ljx.fishpicsbackend.picture.entity.FileResource;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.FileResourceService;
import hk.ljx.fishpicsbackend.picture.vo.CheckUploadVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import hk.ljx.fishpicsbackend.picture.vo.UploadChunkVO;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.space.service.SpaceService;
import hk.ljx.fishpicsbackend.space.vo.SpaceVO;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.service.UserService;
import static hk.ljx.fishpicsbackend.common.constants.SpaceConstants.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PictureUploadService {
    @Resource private CosService cosService;
    @Resource private PictureMapper pictureMapper;
    @Resource private SpaceMapper spaceMapper;
    @Lazy @Resource private SpaceService spaceService;
    @Lazy @Resource private UserService userService;
    @Resource private FileResourceService fileResourceService;
    @Resource private SpaceQuotaManager quotaManager;
    @Resource private SpaceWritePermissionChecker spaceWritePermissionChecker;
    @Resource private RedisCacheManager cacheManager;
    @Resource private RedisAtomicOps redisAtomicOps;
    @Resource private StringRedisTemplate redis;
    @Resource private PlatformTransactionManager transactionManager;

    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024;
    private static final long DIRECT_UPLOAD_LIMIT = 100L * 1024 * 1024;
    private static final long MAX_CHUNK_SIZE = 5L * 1024 * 1024;
    private static final int MAX_CHUNK_COUNT = 6000;
    private static final String MD5_PATTERN = "^[a-fA-F0-9]{32}$";

    // ========================= Picture Upload =========================

    public String uploadAvatar(MultipartFile file, Long id) {
        User userLogin = LoginContextHelper.requireUser();
        LoginContext ctx = UserHolder.getLoginContext();
        ExcUtils.throwIfTrue(!userLogin.getId().equals(id) && (ctx == null || !ctx.hasSystemPerm("system:user:manage")), "没有权限");
        ExcUtils.throwIfTrue(FileTypeUtils.getValidFileType(file) == null, ExceptionCode.PARAMETER_ERROR, "不支持的文件类型");
        ExcUtils.throwIfTrue(file.getSize() > MAX_AVATAR_SIZE, ExceptionCode.PARAMETER_ERROR, "头像大小不能超过5MB");
        Long targetId = (id != null && !id.equals(userLogin.getId())) ? id : userLogin.getId();
        User user = userService.getById(targetId);
        ExcUtils.throwIfTrue(user == null, "用户不存在");
        String url = cosService.uploadAndGetImageUrl(file);
        String oldAvatar = user.getAvatar();
        user.setAvatar(url);
        try {
            ExcUtils.throwIfFalse(userService.updateById(user), ExceptionCode.DATABASE_ERROR, "上传失败，数据库错误");
        } catch (Exception e) {
            try { cosService.deletePictureByUrl(url); } catch (Exception ex) { log.warn("COS 回滚新头像失败: {}", url, ex); }
            throw e;
        }
        if (oldAvatar != null) {
            try { cosService.deletePictureByUrl(oldAvatar); } catch (Exception e) { log.warn("旧头像删除失败: {}", oldAvatar, e); }
        }
        refreshUserSessionState(user);
        return url;
    }

    @Transactional(rollbackFor = Exception.class)
    public Picture uploadPicture(MultipartFile file, Long targetSpaceId) {
        User userLogin = LoginContextHelper.requireUser();
        Long userId = userLogin.getId();
        long maxSize = getMaxUploadSize(userLogin.getLevel());
        ExcUtils.throwIfTrue(file.getSize() > maxSize, "上传图片大小不能超过" + FileUtil.readableFileSize(maxSize));
        ExcUtils.throwIfTrue(file.getSize() > DIRECT_UPLOAD_LIMIT, "文件超过大小限制，请使用分片上传");
        ExcUtils.throwIfTrue(FileTypeUtils.getValidFileType(file) == null, "上传文件格式不正确");
        String md5;
        try (InputStream is = file.getInputStream()) { md5 = DigestUtil.md5Hex(is); }
        catch (IOException e) { throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "计算文件MD5失败"); }
        return doProcessUpload(() -> cosService.uploadPicture(file), file.getSize(), md5, userId, targetSpaceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Picture savePictureByUrl(String url, Long targetSpaceId) {
        User userLogin = LoginContextHelper.requireUser();
        Long userId = userLogin.getId();
        ExcUtils.throwIfTrue(StrUtil.isBlank(url), "图片URL不能为空");
        File tempFile = DownloadUtils.download(url, getMaxUploadSize(userLogin.getLevel()));
        try {
            try (FileInputStream fis = new FileInputStream(tempFile)) {
                ExcUtils.throwIfTrue(FileTypeUtils.getValidFileType(fis) == null, "不支持的图片格式");
            } catch (IOException e) { throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "读取临时文件失败"); }
            String md5 = DigestUtil.md5Hex(tempFile);
            long fileSize = tempFile.length();
            return doProcessUpload(() -> {
                try (FileInputStream fis2 = new FileInputStream(tempFile)) { return cosService.uploadPicture(fis2, tempFile.length()); }
                catch (IOException e) { throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "读取临时文件失败"); }
            }, fileSize, md5, userId, targetSpaceId);
        } finally { FileUtil.del(tempFile); }
    }

    // ========================= Multipart Upload =========================

    @Transactional(rollbackFor = Exception.class)
    public CheckUploadVO checkUpload(CheckUploadRequest request) {
        validateMd5(request.getMd5());
        ExcUtils.throwIfTrue(request.getSize() == null, "文件大小不能为空");
        User user = LoginContextHelper.requireUser();
        storeBindOwner(request.getMd5(), user.getId());
        long maxSize = getMaxUploadSize(user.getLevel());
        ExcUtils.throwIfTrue(request.getSize() > maxSize, ExceptionCode.PARAMETER_ERROR, "文件大小超过限制（最大" + formatSize(maxSize) + "）");
        FileResource resource = fileResourceService.findByMd5AndSize(request.getMd5(), request.getSize());
        if (resource != null) return handleDuplicateUpload(request, user, resource);
        Long chunkCount = storeUploadedChunkCount(user.getId(), request.getMd5());
        if (chunkCount != null && chunkCount > 0) return resumeUpload(request, user);
        return startNewUpload(request, user);
    }

    public UploadChunkVO uploadChunk(MultipartFile file, String md5, Integer chunkIndex) {
        validateMd5(md5);
        ExcUtils.throwIfTrue(file == null || file.isEmpty(), "分片文件不能为空");
        ExcUtils.throwIfTrue(chunkIndex == null || chunkIndex < 0, "分片编号无效");
        ExcUtils.throwIfTrue(file.getSize() > MAX_CHUNK_SIZE, ExceptionCode.PARAMETER_ERROR, "单个分片不能超过" + formatSize(MAX_CHUNK_SIZE));
        ExcUtils.throwIfTrue(chunkIndex >= MAX_CHUNK_COUNT, ExceptionCode.PARAMETER_ERROR, "分片数量超过限制");
        User user = LoginContextHelper.requireUser();
        storeValidateOwner(md5, user.getId());
        String cosKey = storeGetRequiredCosKey(user.getId(), md5);
        if (storeIsChunkUploaded(user.getId(), md5, chunkIndex)) {
            String existingEtag = storeGetChunkEtag(user.getId(), md5, chunkIndex);
            storeRefreshTtl(md5, user.getId());
            return new UploadChunkVO(existingEtag, chunkIndex);
        }
        String uploadId = coordGetOrCreateUploadId(user.getId(), md5, cosKey);
        String etag = coordUploadPart(file, cosKey, uploadId, chunkIndex);
        storeSaveChunk(user.getId(), md5, chunkIndex, etag, file.getSize());
        return new UploadChunkVO(etag, chunkIndex);
    }

    @Transactional(rollbackFor = Exception.class)
    public PictureVO mergeChunks(MergeChunksRequest request) {
        validateMergeRequest(request);
        User user = LoginContextHelper.requireUser();
        long maxSize = getMaxUploadSize(user.getLevel());
        Map<String, Object> mergeResult = storeGetMergeResult(request.getMd5(), user.getId());
        PictureVO idempotentResult = resolveMergeResultIfAvailable(mergeResult, request.getMd5(), user.getId());
        if (idempotentResult != null) return idempotentResult;
        storeValidateOwner(request.getMd5(), user.getId());
        ExcUtils.throwIfTrue(request.getSize() > maxSize, ExceptionCode.PARAMETER_ERROR, "文件大小超过限制（最大" + formatSize(maxSize) + "）");
        validateUploadedChunks(user.getId(), request);
        String uploadId = storeGetUploadId(user.getId(), request.getMd5());
        ExcUtils.throwIfTrue(StrUtil.isBlank(uploadId), ExceptionCode.PARAMETER_ERROR, "uploadId 不存在");
        List<PartETag> partETags = storeLoadPartETags(user.getId(), request.getMd5(), request.getTotalChunks());
        Space space = resolveTargetSpace(request.getTargetSpaceId(), user.getId());
        spaceWritePermissionChecker.check(space, user.getId());
        long size = storeSumUploadedChunkSizes(user.getId(), request.getMd5(), request.getTotalChunks());
        ExcUtils.throwIfTrue(size > maxSize, ExceptionCode.PARAMETER_ERROR, "file size exceeds limit");
        ExcUtils.throwIfFalse(mergeResult != null || quotaManager.reserve(space, size), ExceptionCode.PARAMETER_ERROR, "空间容量不足");
        String cosKey = storeGetRequiredCosKey(user.getId(), request.getMd5());
        String mergeResultKey = storeMergeResultKey(request.getMd5(), user.getId());
        Picture picture = createMultipartPicture(request.getMd5(), size, cosKey, user.getId(), space);
        saveMergeResultQuietly(mergeResultKey, picture, cosKey, user.getId(), size);
        coordRegisterMultipartMergeAfterCommit(new MultipartMergeContext(
                picture.getId(), picture.getResourceId(), cosKey, uploadId, partETags,
                request.getMd5(), mergeResultKey, user.getId(), space, size));
        return PictureVO.ofUpload(picture.getId(), picture.getUrl());
    }

    private PictureVO resolveMergeResultIfAvailable(Map<String, Object> mergeResult, String md5, Long userId) {
        if (mergeResult == null) {
            return null;
        }
        // 校验 session 归属
        String storedUserId = getMergeResultValue(mergeResult, "userId");
        ExcUtils.throwIfTrue(StrUtil.isBlank(storedUserId) || !storedUserId.equals(String.valueOf(userId)),
                ExceptionCode.FORBIDDEN, "upload session owner mismatch");

        String mergedCosKey = getMergeResultValue(mergeResult, "cosKey");
        if (StrUtil.isNotBlank(mergedCosKey) && isMergedObjectAvailable(mergedCosKey)) {
            PictureVO result = buildPictureVOFromMergeResult(mergeResult);
            storeCleanup(md5, true, userId);
            return result;
        }
        String pictureId = getMergeResultValue(mergeResult, "pictureId");
        if (StrUtil.isNotBlank(pictureId)) {
            Picture existingPicture = pictureMapper.selectById(Long.parseLong(pictureId));
            if (existingPicture != null) {
                storeRestoreMergeResult(md5, userId, existingPicture, mergedCosKey);
                PictureVO result = buildPictureVOFromMergeResult(mergeResult);
                storeCleanup(md5, true, userId);
                return result;
            }
        }
        return null;
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

    private boolean isMergedObjectAvailable(String cosKey) {
        try { cosService.getObjectContentType(cosKey); return true; } catch (BaseException e) { return false; }
    }

    // ========================= Validation & Helpers =========================

    private void validateMergeRequest(MergeChunksRequest request) {
        validateMd5(request.getMd5());
        ExcUtils.throwIfTrue(request.getSize() == null, "文件大小不能为空");
        ExcUtils.throwIfTrue(request.getTotalChunks() == null || request.getTotalChunks() <= 0, "分片总数无效");
        ExcUtils.throwIfTrue(request.getTotalChunks() > MAX_CHUNK_COUNT, ExceptionCode.PARAMETER_ERROR, "分片数量超过限制");
    }
    private void validateMd5(String md5) {
        ExcUtils.throwIfTrue(StrUtil.isBlank(md5), "MD5 cannot be blank");
        ExcUtils.throwIfTrue(!md5.matches(MD5_PATTERN), ExceptionCode.PARAMETER_ERROR, "invalid md5");
    }
    private Space resolveTargetSpace(Long targetSpaceId, Long userId) {
        Space space = targetSpaceId != null ? spaceMapper.selectById(targetSpaceId)
                : spaceMapper.selectOne(new LambdaQueryWrapper<Space>().eq(Space::getUserId, userId).eq(Space::getType, 0).last("LIMIT 1"));
        ExcUtils.throwIfTrue(space == null, "space not found");
        Space.validateActive(space);
        return space;
    }
    private long getMaxUploadSize(Integer level) {
        if (level == null || level <= 0) return UPLOAD_MAX_SIZE_NORMAL;
        return switch (level) { case 1 -> UPLOAD_MAX_SIZE_VIP; case 2 -> UPLOAD_MAX_SIZE_SVIP; default -> UPLOAD_MAX_SIZE_NORMAL; };
    }
    private String formatSize(long bytes) { return FileUtil.readableFileSize(bytes); }
    private String extractPictureName(String cosKey) {
        String fileName = cosKey.substring(cosKey.lastIndexOf('/') + 1);
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }
    private void refreshUserSessionState(User user) {
        userService.refreshUserInfoCache(user);
        cacheManager.getUserPermCache().evict(String.valueOf(user.getId()));
    }

    // ========================= Direct Upload Processing =========================

    private Picture doProcessUpload(Supplier<String> cosUploader, long fileSize, String md5, Long userId, Long targetSpaceId) {
        FileResource existingResource = fileResourceService.findByMd5AndSize(md5, fileSize);
        PictureMetadata pictureMessage;
        FileResource resource;
        String cosKey = null;
        boolean isNewUpload = (existingResource == null);
        if (existingResource != null) {
            resource = fileResourceService.addResource(md5, fileSize, existingResource.getCosKey());
            pictureMessage = cosService.getPictureMetadata(existingResource.getCosKey());
        } else {
            cosKey = cosUploader.get();
            try {
                pictureMessage = cosService.getPictureMetadata(cosKey);
            } catch (Exception e) {
                try { cosService.deletePicture(cosKey); } catch (Exception ex) { log.warn("COS 回滚失败: {}", cosKey, ex); }
                throw e;
            }
            resource = fileResourceService.addResource(md5, fileSize, cosKey);
        }
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureMessage, picture);
        picture.setUserId(userId);
        picture.setResourceId(resource.getId());
        if (picture.getSize() == null && pictureMessage.getSize() != null) picture.setSize(pictureMessage.getSize());
        ExcUtils.throwIfTrue(picture.getSize() == null, ExceptionCode.INTERNAL_SERVER_ERROR, "获取图片大小失败");
        long size = picture.getSize();
        Space space = resolveTargetSpace(targetSpaceId, userId);
        spaceWritePermissionChecker.check(space, userId);
        if (existingResource != null) {
            Picture existingPic = pictureMapper.selectOne(new LambdaQueryWrapper<Picture>()
                    .eq(Picture::getResourceId, resource.getId()).eq(Picture::getUserId, userId)
                    .eq(Picture::getSpaceId, space.getId()).last("LIMIT 1"));
            if (existingPic != null) { fileResourceService.decrementRefCount(resource.getId()); return existingPic; }
        }
        if (!quotaManager.reserve(space, size)) {
            cleanupUploadFailure(isNewUpload ? cosKey : null, resource, null, 0);
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "空间容量不足");
        }
        picture.setSpaceId(space.getId());
        try {
            ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "上传失败，数据库错误");
        } catch (org.springframework.dao.DuplicateKeyException e) {
            cleanupUploadFailure(isNewUpload ? cosKey : null, resource, space, size);
            Picture existing = pictureMapper.selectOne(new LambdaQueryWrapper<Picture>()
                    .eq(Picture::getResourceId, resource.getId()).eq(Picture::getUserId, userId)
                    .eq(Picture::getSpaceId, space.getId()).last("LIMIT 1"));
            if (existing != null) return existing;
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "图片已存在");
        } catch (Exception e) {
            cleanupUploadFailure(isNewUpload ? cosKey : null, resource, space, size);
            throw e;
        }
        return picture;
    }

    private void cleanupUploadFailure(String cosKey, FileResource resource, Space space, long reservedSize) {
        if (cosKey != null) { try { cosService.deletePicture(cosKey); } catch (Exception ex) { log.warn("COS 回滚失败: {}", cosKey, ex); } }
        if (space != null && reservedSize > 0) {
            try { quotaManager.release(space, reservedSize); } catch (Exception ex) { log.warn("空间配额回滚失败: space={}, size={}", space.getId(), reservedSize, ex); }
        }
        if (resource != null) {
            try { fileResourceService.decrementRefCount(resource.getId()); } catch (Exception ex) { log.warn("ref_count 回滚失败: resourceId={}", resource.getId(), ex); }
        }
    }

    // ========================= CheckUpload Helpers =========================

    private CheckUploadVO handleDuplicateUpload(CheckUploadRequest request, User user, FileResource resource) {
        Space space = resolveTargetSpace(request.getTargetSpaceId(), user.getId());
        Picture existingPicture = pictureMapper.selectOne(new LambdaQueryWrapper<Picture>()
                .eq(Picture::getResourceId, resource.getId()).eq(Picture::getUserId, user.getId())
                .eq(Picture::getSpaceId, space.getId()).last("LIMIT 1"));
        if (existingPicture != null) return CheckUploadVO.builder().status("duplicate")
                .picture(PictureVO.ofUpload(existingPicture.getId(), existingPicture.getUrl())).build();
        int refResult = fileResourceService.incrementRefCount(resource.getId());
        ExcUtils.throwIfTrue(refResult == -1, ExceptionCode.DATABASE_ERROR, "文件资源不存在，请重新上传");
        Picture picture = createPictureFromResource(resource, user.getId(), space);
        return CheckUploadVO.builder().status("duplicate").picture(PictureVO.ofUpload(picture.getId(), picture.getUrl())).build();
    }

    private CheckUploadVO resumeUpload(CheckUploadRequest request, User user) {
        String md5 = request.getMd5();
        Set<String> uploadedChunks = storeUploadedChunks(user.getId(), md5);
        String cosKey = storeGetCosKey(user.getId(), md5);
        if (StrUtil.isBlank(cosKey)) { cosKey = cosService.generateKey(); storeSaveCosKey(user.getId(), md5, cosKey); }
        storeRefreshTtl(md5, user.getId());
        return CheckUploadVO.builder().status("resume")
                .uploadedChunks(uploadedChunks != null ? uploadedChunks.stream().map(Integer::parseInt).sorted().toList() : List.of())
                .uploadId(storeGetUploadId(user.getId(), md5)).cosKey(cosKey).build();
    }

    private CheckUploadVO startNewUpload(CheckUploadRequest request, User user) {
        String cosKey = cosService.generateKey();
        storeSaveCosKey(user.getId(), request.getMd5(), cosKey);
        storeClearMergeResult(request.getMd5(), user.getId());
        storeRefreshTtl(request.getMd5(), user.getId());
        return CheckUploadVO.builder().status("new").cosKey(cosKey).build();
    }

    private void validateUploadedChunks(Long userId, MergeChunksRequest request) {
        Long uploadedCount = storeUploadedChunkCount(userId, request.getMd5());
        ExcUtils.throwIfTrue(uploadedCount == null || uploadedCount != request.getTotalChunks().longValue(),
                ExceptionCode.PARAMETER_ERROR, "分片不完整，已上传" + uploadedCount + "/" + request.getTotalChunks());
    }
    private void saveMergeResultQuietly(String mergeResultKey, Picture picture, String cosKey, Long userId, long size) {
        try { storeSaveMergeResult(mergeResultKey, picture, cosKey, userId, size); }
        catch (Exception e) { log.warn("mergeChunks write merge result failed: pictureId={}, err={}", picture.getId(), e.getMessage()); }
    }

    // ========================= Upload Coordinator =========================

    private String coordGetOrCreateUploadId(Long userId, String md5, String cosKey) {
        String uploadId = storeGetUploadId(userId, md5);
        if (StrUtil.isNotBlank(uploadId)) return uploadId;
        String newUploadId = cosService.initiateMultipartUpload(cosKey);
        uploadId = redisAtomicOps.setIfAbsentOrGet(storeUploadIdKey(userId, md5), newUploadId, RedisConstants.FILE_UPLOAD_TTL * 3600);
        if (!newUploadId.equals(uploadId)) {
            log.warn("[uploadChunk] uploadId race: cosKey={}, mine={}, actual={}", cosKey, newUploadId, uploadId);
            cosService.abortMultipartUpload(cosKey, newUploadId);
        }
        return uploadId;
    }

    private String coordUploadPart(MultipartFile file, String cosKey, String uploadId, Integer chunkIndex) {
        try (InputStream inputStream = file.getInputStream()) {
            InputStream uploadStream = inputStream;
            if (chunkIndex == 0) {
                byte[] header = new byte[16];
                int read = inputStream.read(header);
                ExcUtils.throwIfTrue(read <= 0, ExceptionCode.PARAMETER_ERROR, "empty first chunk");
                String fileType = FileTypeUtils.getValidFileType(new ByteArrayInputStream(header, 0, read));
                ExcUtils.throwIfTrue(fileType == null, ExceptionCode.PARAMETER_ERROR, "不支持的图片格式");
                uploadStream = new SequenceInputStream(new ByteArrayInputStream(header, 0, Math.max(read, 0)), inputStream);
            }
            return cosService.uploadPart(cosKey, uploadId, chunkIndex + 1, uploadStream, file.getSize());
        } catch (IOException e) { throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "分片上传失败"); }
    }

    private void coordRegisterMultipartMergeAfterCommit(MultipartMergeContext ctx) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                boolean mergeOk = coordTryCompleteMultipartUpload(ctx.cosKey(), ctx.uploadId(), ctx.partETags(), ctx.md5(), ctx.mergeResultKey(), ctx.userId());
                if (!mergeOk) { coordHandleMultipartMergeFailure(ctx); return; }
                coordUpdateMergedPictureMetadata(ctx.pictureId(), ctx.cosKey());
            }
        });
    }

    private boolean coordTryCompleteMultipartUpload(String cosKey, String uploadId, List<PartETag> partETags, String md5, String mergeResultKey, Long userId) {
        try {
            cosService.completeMultipartUpload(cosKey, uploadId, partETags);
            log.info("COS multipart merge completed: cosKey={}", cosKey);
            storeCleanup(md5, true, userId);
            return true;
        } catch (Exception e) {
            if (isMergedObjectAvailable(cosKey)) {
                log.warn("COS merge returned an error but object is already available: cosKey={}", cosKey, e);
                storeCleanup(md5, true, userId);
                return true;
            }
            cosService.abortMultipartUpload(cosKey, uploadId);
            storeKeepMergeResult(mergeResultKey);
            log.error("COS multipart merge failed: cosKey={}, md5={}, error={}", cosKey, md5, e.getMessage());
            return false;
        }
    }

    private void coordHandleMultipartMergeFailure(MultipartMergeContext ctx) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status -> {
            try {
                pictureMapper.deleteById(ctx.pictureId());
                log.error("[mergeChunks] COS merge failed, DB picture deleted: pictureId={}, cosKey={}", ctx.pictureId(), ctx.cosKey());
                quotaManager.release(ctx.space(), ctx.size());
                if (ctx.resourceId() != null) fileResourceService.decrementRefCount(ctx.resourceId());
                storeCleanup(ctx.md5(), true, ctx.userId());
            } catch (Exception ex) {
                log.error("[mergeChunks] cleanup after COS merge failure partially failed: pictureId={}, cosKey={}", ctx.pictureId(), ctx.cosKey(), ex);
            }
        });
    }

    private void coordUpdateMergedPictureMetadata(Long pictureId, String cosKey) {
        try {
            PictureMetadata metadata = cosService.getPictureMetadata(cosKey);
            if (metadata != null && (metadata.getWidth() != null || metadata.getHeight() != null)) {
                Picture update = new Picture();
                update.setId(pictureId);
                update.setWidth(metadata.getWidth());
                update.setHeight(metadata.getHeight());
                pictureMapper.updateById(update);
            }
        } catch (Exception e) { log.warn("failed to read metadata after multipart merge: cosKey={}", cosKey, e); }
    }

    // ========================= Session Store (Redis) =========================

    private void storeBindOwner(String md5, Long userId) {
        redis.opsForValue().set(RedisConstants.getUserFileUploadOwnerKey(userId, md5), String.valueOf(userId), RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
    }
    private void storeValidateOwner(String md5, Long userId) {
        String owner = redis.opsForValue().get(RedisConstants.getUserFileUploadOwnerKey(userId, md5));
        ExcUtils.throwIfTrue(StrUtil.isBlank(owner), ExceptionCode.PARAMETER_ERROR, "上传会话已过期，请重新上传");
        ExcUtils.throwIfTrue(!owner.equals(String.valueOf(userId)), ExceptionCode.FORBIDDEN, "该上传会话不属于当前用户");
    }
    private Long storeUploadedChunkCount(Long userId, String md5) {
        return redis.opsForSet().size(RedisConstants.getFileUploadChunksKey(userId, md5));
    }
    private Set<String> storeUploadedChunks(Long userId, String md5) {
        return redis.opsForSet().members(RedisConstants.getFileUploadChunksKey(userId, md5));
    }
    private boolean storeIsChunkUploaded(Long userId, String md5, Integer chunkIndex) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(RedisConstants.getFileUploadChunksKey(userId, md5), String.valueOf(chunkIndex)));
    }
    private String storeGetChunkEtag(Long userId, String md5, Integer chunkIndex) {
        Object etag = redis.opsForHash().get(RedisConstants.getFileChunkEtagKey(userId, md5), String.valueOf(chunkIndex));
        return etag == null ? "" : etag.toString();
    }
    private void storeSaveChunk(Long userId, String md5, Integer chunkIndex, String etag, long size) {
        String idx = String.valueOf(chunkIndex);
        redis.opsForSet().add(RedisConstants.getFileUploadChunksKey(userId, md5), idx);
        redis.opsForHash().put(RedisConstants.getFileChunkEtagKey(userId, md5), idx, etag);
        redis.opsForHash().put(RedisConstants.getFileChunkSizeKey(userId, md5), idx, String.valueOf(size));
        storeRefreshTtl(md5, userId);
    }
    private String storeGetUploadId(Long userId, String md5) { return redis.opsForValue().get(RedisConstants.getFileUploadIdKey(userId, md5)); }
    private String storeUploadIdKey(Long userId, String md5) { return RedisConstants.getFileUploadIdKey(userId, md5); }
    private String storeGetCosKey(Long userId, String md5) { return redis.opsForValue().get(RedisConstants.getFileCosKeyKey(userId, md5)); }
    private String storeGetRequiredCosKey(Long userId, String md5) {
        String cosKey = storeGetCosKey(userId, md5);
        ExcUtils.throwIfTrue(StrUtil.isBlank(cosKey), ExceptionCode.PARAMETER_ERROR, "上传会话已过期，请重新上传");
        return cosKey;
    }
    private void storeSaveCosKey(Long userId, String md5, String cosKey) {
        redis.opsForValue().set(RedisConstants.getFileCosKeyKey(userId, md5), cosKey, RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
    }
    private void storeClearMergeResult(String md5, Long userId) { redis.delete(RedisConstants.getFileMergeResultKey(userId, md5)); }
    private Map<String, Object> storeGetMergeResult(String md5, Long userId) {
        String raw = redis.opsForValue().get(RedisConstants.getFileMergeResultKey(userId, md5));
        return StrUtil.isBlank(raw) ? null : JSONUtil.toBean(raw, Map.class);
    }
    private void storeRestoreMergeResult(String md5, Long userId, Picture picture, String cosKey) {
        Map<String, String> data = new HashMap<>();
        data.put("pictureId", String.valueOf(picture.getId()));
        data.put("url", picture.getUrl());
        data.put("userId", String.valueOf(picture.getUserId()));
        data.put("cosKey", cosKey != null ? cosKey : "");
        data.put("size", String.valueOf(picture.getSize()));
        redis.opsForValue().set(RedisConstants.getFileMergeResultKey(userId, md5), JSONUtil.toJsonStr(data), RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
    }
    private void storeSaveMergeResult(String mergeResultKey, Picture picture, String cosKey, Long userId, long size) {
        Map<String, String> data = new HashMap<>();
        data.put("pictureId", String.valueOf(picture.getId()));
        data.put("url", picture.getUrl());
        data.put("userId", String.valueOf(userId));
        data.put("cosKey", cosKey);
        data.put("size", String.valueOf(size));
        redis.opsForValue().set(mergeResultKey, JSONUtil.toJsonStr(data), RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
    }
    private String storeMergeResultKey(String md5, Long userId) { return RedisConstants.getFileMergeResultKey(userId, md5); }
    private void storeKeepMergeResult(String mergeResultKey) { redis.expire(mergeResultKey, RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS); }
    private long storeSumUploadedChunkSizes(Long userId, String md5, Integer totalChunks) {
        Map<Object, Object> chunkSizeMap = redis.opsForHash().entries(RedisConstants.getFileChunkSizeKey(userId, md5));
        ExcUtils.throwIfTrue(chunkSizeMap.size() != totalChunks, ExceptionCode.PARAMETER_ERROR, "chunk size data is incomplete");
        long totalSize = 0L;
        for (Object value : chunkSizeMap.values()) {
            ExcUtils.throwIfTrue(value == null, ExceptionCode.PARAMETER_ERROR, "invalid chunk size");
            totalSize += Long.parseLong(value.toString());
        }
        return totalSize;
    }
    private List<PartETag> storeLoadPartETags(Long userId, String md5, Integer totalChunks) {
        Map<Object, Object> etagMap = redis.opsForHash().entries(RedisConstants.getFileChunkEtagKey(userId, md5));
        ExcUtils.throwIfTrue(etagMap.size() != totalChunks, ExceptionCode.PARAMETER_ERROR, "分片 ETag 不完整，请重试合并");
        return etagMap.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> Integer.parseInt(entry.getKey().toString())))
                .map(entry -> new PartETag(Integer.parseInt(entry.getKey().toString()) + 1, entry.getValue().toString()))
                .collect(Collectors.toList());
    }
    private void storeRefreshTtl(String md5, Long userId) {
        redis.expire(RedisConstants.getUserFileUploadOwnerKey(userId, md5), RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        redis.expire(RedisConstants.getFileCosKeyKey(userId, md5), RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        redis.expire(RedisConstants.getFileUploadIdKey(userId, md5), RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        redis.expire(RedisConstants.getFileUploadChunksKey(userId, md5), RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        redis.expire(RedisConstants.getFileChunkEtagKey(userId, md5), RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        redis.expire(RedisConstants.getFileChunkSizeKey(userId, md5), RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        redis.expire(RedisConstants.getFileMergeResultKey(userId, md5), RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
    }
    private void storeCleanup(String md5, boolean deleteMergeResult, Long userId) {
        Long uid = userId != null ? userId : 0L;
        redis.delete(RedisConstants.getFileUploadChunksKey(uid, md5));
        redis.delete(RedisConstants.getFileUploadIdKey(uid, md5));
        redis.delete(RedisConstants.getFileChunkEtagKey(uid, md5));
        redis.delete(RedisConstants.getFileChunkSizeKey(uid, md5));
        redis.delete(RedisConstants.getFileCosKeyKey(uid, md5));
        if (userId != null) redis.delete(RedisConstants.getUserFileUploadOwnerKey(userId, md5));
        if (deleteMergeResult) redis.delete(RedisConstants.getFileMergeResultKey(uid, md5));
    }

    // ========================= Picture Creation Helpers =========================

    private Picture createMultipartPicture(String md5, long size, String cosKey, Long userId, Space space) {
        FileResource resource = fileResourceService.addResource(md5, size, cosKey);
        Picture picture = new Picture();
        picture.setUrl(cosService.getImageUrl(cosKey));
        picture.setPictureName(extractPictureName(cosKey));
        picture.setUserId(userId);
        picture.setSize(size);
        picture.setSpaceId(space.getId());
        picture.setResourceId(resource.getId());
        ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "保存图片失败");
        return picture;
    }

    private Picture createPictureFromResource(FileResource resource, Long userId, Space space) {
        PictureMetadata metadata = cosService.getPictureMetadata(resource.getCosKey());
        spaceWritePermissionChecker.check(space, userId);
        long size = resource.getSize();
        ExcUtils.throwIfFalse(quotaManager.reserve(space, size), ExceptionCode.PARAMETER_ERROR, "space quota exceeded");
        Picture picture = new Picture();
        BeanUtil.copyProperties(metadata, picture);
        picture.setUserId(userId);
        picture.setSize(resource.getSize());
        picture.setSpaceId(space.getId());
        picture.setResourceId(resource.getId());
        try {
            ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "save picture failed");
        } catch (Exception e) { rollbackDuplicateUpload(space, size, resource.getId()); throw e; }
        return picture;
    }

    private void rollbackDuplicateUpload(Space space, long size, Long resourceId) {
        try { quotaManager.release(space, size); } catch (Exception ex) { log.warn("duplicate upload quota rollback failed: space={}, size={}", space.getId(), size, ex); }
        try { fileResourceService.decrementRefCount(resourceId); } catch (Exception ex) { log.warn("duplicate upload ref_count rollback failed: resourceId={}", resourceId, ex); }
    }

    record MultipartMergeContext(Long pictureId, Long resourceId, String cosKey, String uploadId,
                                  List<PartETag> partETags, String md5, String mergeResultKey,
                                  Long userId, Space space, long size) {}
}
