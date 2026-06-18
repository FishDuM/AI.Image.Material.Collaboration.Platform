package hk.ljx.fishpicsbackend.picture.component;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.infra.CosService;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.picture.dto.MergeChunksRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureMetadata;
import hk.ljx.fishpicsbackend.picture.entity.FileResource;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.FileResourceService;
import hk.ljx.fishpicsbackend.space.entity.Space;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static hk.ljx.fishpicsbackend.common.constants.SpaceConstants.UPLOAD_MAX_SIZE_NORMAL;
import static hk.ljx.fishpicsbackend.common.constants.SpaceConstants.UPLOAD_MAX_SIZE_SVIP;
import static hk.ljx.fishpicsbackend.common.constants.SpaceConstants.UPLOAD_MAX_SIZE_VIP;
import static hk.ljx.fishpicsbackend.picture.constants.PictureConstants.STATUS_APPROVED;
import static hk.ljx.fishpicsbackend.picture.constants.PictureConstants.STATUS_PENDING_REVIEW;

@Slf4j
@Component
public class MultipartUploadSupport {

    private static final int MAX_CHUNK_COUNT = 6000;
    private static final String MD5_PATTERN = "^[a-fA-F0-9]{32}$";

    @Resource
    private CosService cosService;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private FileResourceService fileResourceService;

    @Resource
    private SpaceQuotaManager quotaManager;

    @Resource
    private SpaceWritePermissionChecker spaceWritePermissionChecker;

    public void validateMergeRequest(MergeChunksRequest request) {
        validateMd5(request.getMd5());
        ExcUtils.throwIfTrue(request.getSize() == null, "文件大小不能为空");
        ExcUtils.throwIfTrue(request.getTotalChunks() == null || request.getTotalChunks() <= 0, "分片总数无效");
        ExcUtils.throwIfTrue(request.getTotalChunks() > MAX_CHUNK_COUNT,
                ExceptionCode.PARAMETER_ERROR, "分片数量超过限制");
    }

    public void validateMd5(String md5) {
        ExcUtils.throwIfTrue(StrUtil.isBlank(md5), "MD5 cannot be blank");
        ExcUtils.throwIfTrue(!md5.matches(MD5_PATTERN), ExceptionCode.PARAMETER_ERROR, "invalid md5");
    }

    public Space resolveTargetSpace(Long targetSpaceId, Long userId) {
        Space space = targetSpaceId != null
                ? spaceMapper.selectById(targetSpaceId)
                : spaceMapper.selectOne(new LambdaQueryWrapper<Space>()
                        .eq(Space::getUserId, userId)
                        .eq(Space::getType, 0)
                        .last("LIMIT 1"));
        ExcUtils.throwIfTrue(space == null, "space not found");
        Space.validateActive(space);
        return space;
    }

    public long getMaxUploadSize(Integer level) {
        if (level == null || level <= 0) {
            return UPLOAD_MAX_SIZE_NORMAL;
        }
        return switch (level) {
            case 1 -> UPLOAD_MAX_SIZE_VIP;
            case 2 -> UPLOAD_MAX_SIZE_SVIP;
            default -> UPLOAD_MAX_SIZE_SVIP;
        };
    }

    public String formatSize(long bytes) {
        return FileUtil.readableFileSize(bytes);
    }

    public Picture createMultipartPicture(String md5, long size, String cosKey, Long userId, Space space) {
        FileResource resource = fileResourceService.addResource(md5, size, cosKey);
        Picture picture = new Picture();
        picture.setUrl(cosService.getImageUrl(cosKey));
        picture.setPictureName(extractPictureName(cosKey));
        picture.setUserId(userId);
        picture.setSize(size);
        picture.setSpaceId(space.getId());
        picture.setResourceId(resource.getId());
        picture.setStatus(resolveInitialPictureStatus());
        ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "保存图片失败");
        return picture;
    }

    public Picture createPictureFromResource(FileResource resource, Long userId, Space space) {
        PictureMetadata metadata = cosService.getPictureMetadata(resource.getCosKey());
        spaceWritePermissionChecker.check(space, userId);
        long size = resource.getSize();
        ExcUtils.throwIfFalse(quotaManager.reserve(space, size),
                ExceptionCode.PARAMETER_ERROR, "space quota exceeded");

        Picture picture = new Picture();
        BeanUtil.copyProperties(metadata, picture);
        picture.setUserId(userId);
        picture.setSize(resource.getSize());
        picture.setSpaceId(space.getId());
        picture.setResourceId(resource.getId());
        picture.setStatus(resolveInitialPictureStatus());

        try {
            ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "save picture failed");
        } catch (Exception e) {
            rollbackDuplicateUpload(space, size, resource.getId());
            throw e;
        }
        return picture;
    }

    private void rollbackDuplicateUpload(Space space, long size, Long resourceId) {
        try {
            quotaManager.release(space, size);
        } catch (Exception ex) {
            log.warn("duplicate upload quota rollback failed: space={}, size={}", space.getId(), size, ex);
        }
        try {
            fileResourceService.decrementRefCount(resourceId);
        } catch (Exception ex) {
            log.warn("duplicate upload ref_count rollback failed: resourceId={}", resourceId, ex);
        }
    }

    private String extractPictureName(String cosKey) {
        String fileName = cosKey.substring(cosKey.lastIndexOf('/') + 1);
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    private int resolveInitialPictureStatus() {
        LoginContext context = UserHolder.getLoginContext();
        return context != null && context.hasSystemPerm("system:user:manage")
                ? STATUS_APPROVED
                : STATUS_PENDING_REVIEW;
    }
}
