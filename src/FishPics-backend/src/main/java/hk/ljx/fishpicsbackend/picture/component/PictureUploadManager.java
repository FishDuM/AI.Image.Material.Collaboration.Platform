package hk.ljx.fishpicsbackend.picture.component;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import hk.ljx.fishpicsbackend.common.cache.RedisCacheManager;
import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.infra.CosService;
import hk.ljx.fishpicsbackend.common.utils.DownloadUtils;
import hk.ljx.fishpicsbackend.common.utils.FileTypeUtils;
import hk.ljx.fishpicsbackend.common.utils.LoginContextHelper;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.picture.dto.CheckUploadRequest;
import hk.ljx.fishpicsbackend.picture.dto.MergeChunksRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureMetadata;
import hk.ljx.fishpicsbackend.picture.entity.FileResource;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.service.FileResourceService;
import hk.ljx.fishpicsbackend.picture.service.PicturePermissionUtil;
import hk.ljx.fishpicsbackend.picture.vo.CheckUploadVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import hk.ljx.fishpicsbackend.picture.vo.UploadChunkVO;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.space.service.SpaceService;
import hk.ljx.fishpicsbackend.space.vo.SpaceVO;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;

/**
 * 图片上传逻辑（直传、URL上传、头像上传、分片上传委托）
 * 从 PictureServiceImpl 拆分，降低主服务复杂度
 */
@Slf4j
@Component
public class PictureUploadManager {

    @Resource
    private CosService cosService;

    @Resource
    private PictureMapper pictureMapper;

    @Lazy
    @Resource
    private SpaceService spaceService;

    @Lazy
    @Resource
    private UserService userService;

    @Resource
    private FileResourceService fileResourceService;

    @Resource
    private MultipartUploadManager multipartUploadManager;

    @Resource
    private SpaceQuotaManager quotaManager;

    @Resource
    private SpaceWritePermissionChecker spaceWritePermissionChecker;

    @Resource
    private MultipartUploadSupport multipartUploadSupport;

    @Resource
    private RedisCacheManager cacheManager;

    /** 头像文件大小限制 5MB */
    private static final long MAX_AVATAR_SIZE = 5 * 1024 * 1024;
    /** 直传文件大小限制 100MB */
    private static final long DIRECT_UPLOAD_LIMIT = 100L * 1024 * 1024;

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
            try {
                cosService.deletePictureByUrl(url);
            } catch (Exception ex) {
                log.warn("COS 回滚新头像失败: {}", url, ex);
            }
            throw e;
        }
        if (oldAvatar != null) {
            try {
                cosService.deletePictureByUrl(oldAvatar);
            } catch (Exception e) {
                log.warn("旧头像删除失败: {}", oldAvatar, e);
            }
        }
        refreshUserSessionState(user);
        return url;
    }

    @Transactional(rollbackFor = Exception.class)
    public Picture uploadPicture(MultipartFile file, Long targetSpaceId) {
        User userLogin = LoginContextHelper.requireUser();
        Long userId = userLogin.getId();

        long maxSize = multipartUploadSupport.getMaxUploadSize(userLogin.getLevel());
        ExcUtils.throwIfTrue(file.getSize() > maxSize,
                "上传图片大小不能超过" + FileUtil.readableFileSize(maxSize));
        ExcUtils.throwIfTrue(file.getSize() > DIRECT_UPLOAD_LIMIT,
                "文件超过大小限制，请使用分片上传");
        String validFileType = FileTypeUtils.getValidFileType(file);
        ExcUtils.throwIfTrue(validFileType == null, "上传文件格式不正确");
        String md5;
        try (InputStream is = file.getInputStream()) {
            md5 = DigestUtil.md5Hex(is);
        } catch (IOException e) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "计算文件MD5失败");
        }

        return doProcessUpload(() -> cosService.uploadPicture(file), file.getSize(), md5, userId, targetSpaceId);
    }

    @Transactional(rollbackFor = Exception.class)
    public Picture savePictureByUrl(String url, Long targetSpaceId) {
        User userLogin = LoginContextHelper.requireUser();
        Long userId = userLogin.getId();
        ExcUtils.throwIfTrue(cn.hutool.core.util.StrUtil.isBlank(url), "图片URL不能为空");

        long maxSize = multipartUploadSupport.getMaxUploadSize(userLogin.getLevel());
        File tempFile = DownloadUtils.download(url, maxSize);
        try {
            try (FileInputStream fis = new FileInputStream(tempFile)) {
                String fileType = FileTypeUtils.getValidFileType(fis);
                ExcUtils.throwIfTrue(fileType == null, "不支持的图片格式");
            } catch (IOException e) {
                throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "读取临时文件失败");
            }

            String md5 = DigestUtil.md5Hex(tempFile);
            long fileSize = tempFile.length();

            return doProcessUpload(() -> {
                try (FileInputStream fis2 = new FileInputStream(tempFile)) {
                    return cosService.uploadPicture(fis2, tempFile.length());
                } catch (IOException e) {
                    throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "读取临时文件失败");
                }
            }, fileSize, md5, userId, targetSpaceId);

        } finally {
            FileUtil.del(tempFile);
        }
    }

    public CheckUploadVO checkUpload(CheckUploadRequest request) {
        return multipartUploadManager.checkUpload(request);
    }

    public UploadChunkVO uploadChunk(MultipartFile file, String md5, Integer chunkIndex) {
        return multipartUploadManager.uploadChunk(file, md5, chunkIndex);
    }

    public PictureVO mergeChunks(MergeChunksRequest request) {
        return multipartUploadManager.mergeChunks(request);
    }

    private Picture doProcessUpload(Supplier<String> cosUploader, long fileSize, String md5,
                                     Long userId, Long targetSpaceId) {
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
            pictureMessage = cosService.getPictureMetadata(cosKey);
            resource = fileResourceService.addResource(md5, fileSize, cosKey);
        }

        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureMessage, picture);
        picture.setUserId(userId);
        picture.setResourceId(resource.getId());
        if (picture.getSize() == null && pictureMessage.getSize() != null) {
            picture.setSize(pictureMessage.getSize());
        }
        ExcUtils.throwIfTrue(picture.getSize() == null, ExceptionCode.INTERNAL_SERVER_ERROR, "获取图片大小失败");

        long size = picture.getSize();
        Space space = resolveTargetSpace(targetSpaceId);
        spaceWritePermissionChecker.check(space, userId);

        if (existingResource != null) {
            Picture existingPic = pictureMapper.selectOne(new LambdaQueryWrapper<Picture>()
                    .eq(Picture::getResourceId, resource.getId())
                    .eq(Picture::getUserId, userId)
                    .eq(Picture::getSpaceId, space.getId())
                    .last("LIMIT 1"));
            if (existingPic != null) {
                fileResourceService.decrementRefCount(resource.getId());
                return existingPic;
            }
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
                    .eq(Picture::getResourceId, resource.getId())
                    .eq(Picture::getUserId, userId)
                    .eq(Picture::getSpaceId, space.getId())
                    .last("LIMIT 1"));
            if (existing != null) return existing;
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "图片已存在");
        } catch (Exception e) {
            cleanupUploadFailure(isNewUpload ? cosKey : null, resource, space, size);
            throw e;
        }
        return picture;
    }

    private Space resolveTargetSpace(Long targetSpaceId) {
        if (targetSpaceId != null) {
            Space space = spaceService.getById(targetSpaceId);
            ExcUtils.throwIfTrue(space == null, "目标空间不存在");
            return space;
        }
        List<SpaceVO> spaceList = spaceService.listSpace(0);
        ExcUtils.throwIfTrue(spaceList == null || spaceList.isEmpty(), "私人空间不存在，请联系管理员");
        return spaceService.getById(spaceList.get(0).getId());
    }

    private void cleanupUploadFailure(String cosKey, FileResource resource, Space space, long reservedSize) {
        if (cosKey != null) {
            try {
                cosService.deletePicture(cosKey);
            } catch (Exception ex) {
                log.warn("COS 回滚失败: {}", cosKey, ex);
            }
        }
        if (space != null && reservedSize > 0) {
            try {
                quotaManager.release(space, reservedSize);
            } catch (Exception ex) {
                log.warn("空间配额回滚失败: space={}, size={}", space.getId(), reservedSize, ex);
            }
        }
        if (resource != null) {
            try {
                fileResourceService.decrementRefCount(resource.getId());
            } catch (Exception ex) {
                log.warn("ref_count 回滚失败: resourceId={}", resource.getId(), ex);
            }
        }
    }

    private void refreshUserSessionState(User user) {
        userService.refreshUserInfoCache(user);
        cacheManager.getUserPermCache().evict(String.valueOf(user.getId()));
    }

}
