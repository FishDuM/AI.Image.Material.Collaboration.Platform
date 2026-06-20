package hk.ljx.fishpicsbackend.picture.component;

import cn.hutool.crypto.digest.DigestUtil;
import hk.ljx.fishpicsbackend.collab.CollabMessageFactory;
import hk.ljx.fishpicsbackend.collab.CollabSessionRegistry;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.infra.CosService;
import hk.ljx.fishpicsbackend.common.utils.FileTypeUtils;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import hk.ljx.fishpicsbackend.picture.dto.PictureMetadata;
import hk.ljx.fishpicsbackend.picture.entity.FileResource;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.FileResourceService;
import hk.ljx.fishpicsbackend.picture.service.PicturePermissionUtil;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.user.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
public class PictureReplaceManager {

    @Resource
    private CosService cosService;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private FileResourceService fileResourceService;

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private SpaceQuotaManager quotaManager;

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    @Resource
    private CollabSessionRegistry collabSessionRegistry;

    @Transactional(rollbackFor = Exception.class)
    public PictureVO replace(Long pictureId, MultipartFile file, boolean requireCollabLock) {
        ExcUtils.throwIfTrue(FileTypeUtils.getValidFileType(file) == null,
                ExceptionCode.PARAMETER_ERROR, "不支持的文件类型");
        User user = LoginContextHelper.requireUser();

        Picture picture = pictureMapper.selectById(pictureId);
        ExcUtils.throwIfTrue(picture == null || picture.getUserId() == null,
                ExceptionCode.NOT_FOUND, "图片不存在");

        ensureCollabLockIfRequired(picture, pictureId, user, requireCollabLock);

        String md5 = calculateMd5(file);
        Long oldResourceId = picture.getResourceId();
        FileResource existingByMd5 = fileResourceService.findByMd5AndSize(md5, file.getSize());
        if (isSameResource(existingByMd5, oldResourceId)) {
            return unchangedResult(picture);
        }

        boolean uploadedNewFile = existingByMd5 == null;
        String newCosKey = uploadedNewFile ? cosService.uploadPicture(file) : existingByMd5.getCosKey();
        PictureMetadata metadata = cosService.getPictureMetadata(newCosKey);
        FileResource newResource = fileResourceService.addResource(md5, file.getSize(), newCosKey);

        long oldSize = picture.getSize() != null ? picture.getSize() : 0L;
        long newSize = newResource.getSize();
        long reservedSizeDiff = reserveMoreQuotaIfNeeded(picture.getSpaceId(), newSize - oldSize,
                newResource, uploadedNewFile ? newCosKey : null);

        String oldUrl = picture.getUrl();
        updatePictureResource(picture, metadata, newResource, uploadedNewFile ? newCosKey : null, reservedSizeDiff);
        decrementOldResource(oldResourceId);
        broadcastFileReplacedAfterCommit(picture.getSpaceId(), pictureId, user);

        log.info("picture file replaced: pictureId={}, oldUrl={}, newUrl={}", pictureId, oldUrl, picture.getUrl());
        return buildResult(pictureId, picture.getUrl());
    }

    private void ensureCollabLockIfRequired(Picture picture, Long pictureId, User user, boolean requireCollabLock) {
        if (requireCollabLock && !collabSessionRegistry.isEditLockHolder(picture.getSpaceId(), pictureId, user.getId())) {
            throw new BaseException(ExceptionCode.FORBIDDEN, "请先获取协同编辑权");
        }
    }

    private String calculateMd5(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            return DigestUtil.md5Hex(inputStream);
        } catch (IOException e) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "计算文件MD5失败");
        }
    }

    private boolean isSameResource(FileResource existingByMd5, Long oldResourceId) {
        return existingByMd5 != null && oldResourceId != null && existingByMd5.getId().equals(oldResourceId);
    }

    private PictureVO unchangedResult(Picture picture) {
        PictureVO vo = new PictureVO();
        vo.setUrl(picture.getUrl());
        vo.setUpdateTime(picture.getUpdateTime());
        return vo;
    }

    private long reserveMoreQuotaIfNeeded(Long spaceId, long sizeDiff, FileResource newResource, String newCosKey) {
        if (sizeDiff <= 0 || spaceId == null) {
            return 0L;
        }
        Space space = spaceMapper.selectById(spaceId);
        ExcUtils.throwIfTrue(space == null, ExceptionCode.NOT_FOUND, "图片所属空间不存在");
        if (quotaManager.reserve(space, sizeDiff)) {
            return sizeDiff;
        }
        rollbackReplacementResource(newResource, newCosKey, 0L, null);
        throw new BaseException(ExceptionCode.PARAMETER_ERROR, "空间容量不足,无法保存");
    }

    private void updatePictureResource(Picture picture, PictureMetadata metadata, FileResource newResource,
                                       String newCosKey, long reservedSizeDiff) {
        picture.setUrl(metadata.getUrl());
        picture.setResourceId(newResource.getId());
        picture.setSize(newResource.getSize());
        if (metadata.getPictureName() != null) {
            picture.setPictureName(metadata.getPictureName());
        }
        try {
            ExcUtils.throwIfTrue(pictureMapper.updateById(picture) != 1,
                    ExceptionCode.INTERNAL_SERVER_ERROR, "更新失败");
        } catch (Exception e) {
            rollbackReplacementResource(newResource, newCosKey, reservedSizeDiff, picture.getSpaceId());
            throw e;
        }
    }

    private void decrementOldResource(Long oldResourceId) {
        if (oldResourceId == null) {
            return;
        }
        try {
            fileResourceService.decrementRefCount(oldResourceId);
        } catch (Exception e) {
            log.warn("old picture resource rollback failed: resourceId={}", oldResourceId, e);
        }
    }

    private void rollbackReplacementResource(FileResource newResource, String newCosKey, long sizeDiff, Long spaceId) {
        if (newCosKey != null) {
            try {
                cosService.deletePicture(newCosKey);
            } catch (Exception ex) {
                log.warn("replace COS rollback failed: {}", newCosKey, ex);
            }
        }
        if (newResource != null) {
            try {
                fileResourceService.decrementRefCount(newResource.getId());
            } catch (Exception ex) {
                log.warn("replace ref_count rollback failed: resourceId={}", newResource.getId(), ex);
            }
        }
        if (sizeDiff > 0 && spaceId != null) {
            Space space = spaceMapper.selectById(spaceId);
            if (space != null) {
                try {
                    quotaManager.release(space, sizeDiff);
                } catch (Exception ex) {
                    log.warn("replace quota rollback failed: space={}, sizeDiff={}", spaceId, sizeDiff, ex);
                }
            }
        }
    }

    private void broadcastFileReplacedAfterCommit(Long spaceId, Long pictureId, User user) {
        if (spaceId == null) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    collabSessionRegistry.broadcastAll(
                            spaceId,
                            CollabMessageFactory.fileReplaced(
                                    new CollabMessageFactory.PictureUserMessage(
                                            pictureId, user.getId(), user.getNickname())));
                    collabSessionRegistry.clearPictureState(spaceId, pictureId);
                } catch (Exception e) {
                    log.warn("[replacePictureFile] failed to broadcast file-replaced: pictureId={}", pictureId, e);
                }
            }
        });
    }

    private PictureVO buildResult(Long pictureId, String url) {
        PictureVO result = new PictureVO();
        result.setUrl(url);
        Picture updated = pictureMapper.selectById(pictureId);
        if (updated != null) {
            result.setUpdateTime(updated.getUpdateTime());
        }
        return result;
    }
}
