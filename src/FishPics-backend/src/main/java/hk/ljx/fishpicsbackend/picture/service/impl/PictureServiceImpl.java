package hk.ljx.fishpicsbackend.picture.service.impl;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.entity.FileResource;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.cache.MultiLevelCacheManager;
import hk.ljx.fishpicsbackend.collab.CollabEventPublisher;
import hk.ljx.fishpicsbackend.collab.model.CollabEvent;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.CosService;
import hk.ljx.fishpicsbackend.common.utils.DistributedLockService;
import hk.ljx.fishpicsbackend.common.utils.DownloadUtils;
import hk.ljx.fishpicsbackend.common.utils.FileTypeUtils;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.mapper.SpaceTeamMemberMapper;
import static hk.ljx.fishpicsbackend.common.constants.SpaceConstants.*;
import hk.ljx.fishpicsbackend.picture.dto.AdminPictureListDTO;
import hk.ljx.fishpicsbackend.picture.dto.CheckUploadRequest;
import hk.ljx.fishpicsbackend.picture.dto.DeleteByIdList;
import hk.ljx.fishpicsbackend.picture.dto.MergeChunksRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureMessage;
import hk.ljx.fishpicsbackend.picture.dto.PictureQueryRequest;
import hk.ljx.fishpicsbackend.picture.dto.PictureUpdateRequest;
import hk.ljx.fishpicsbackend.picture.service.FileResourceService;
import hk.ljx.fishpicsbackend.picture.service.PictureService;
import hk.ljx.fishpicsbackend.picture.vo.PictureVO;
import hk.ljx.fishpicsbackend.space.entity.Space;
import hk.ljx.fishpicsbackend.space.service.SpaceService;
import hk.ljx.fishpicsbackend.system.service.PicSystemService;
import hk.ljx.fishpicsbackend.user.entity.User;
import hk.ljx.fishpicsbackend.user.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import com.qcloud.cos.model.PartETag;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author 30574
 * @description 针对表【picture(图片表)】的数据库操作Service实现
 * @createDate 2026-04-13 21:24:49
 */
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private CosService cosService;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private hk.ljx.fishpicsbackend.mapper.SpaceMapper spaceMapper;

    @Lazy
    @Resource
    private SpaceService spaceService;

    @Lazy
    @Resource
    private UserService userService;

    @Resource
    private PicSystemService picSystemService;

    @Resource
    private FileResourceService fileResourceService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SpaceTeamMemberMapper spaceTeamMemberMapper;

    @Resource
    private MultiLevelCacheManager cacheManager;

    @Resource
    private hk.ljx.fishpicsbackend.mapper.PictureTagMapper pictureTagMapper;

    @Resource
    private DistributedLockService distributedLockService;

    @Resource
    private CollabEventPublisher collabEventPublisher;

    @Resource
    private hk.ljx.fishpicsbackend.common.utils.RedisAtomicOps redisAtomicOps;

    @Resource
    private hk.ljx.fishpicsbackend.mapper.PictureShareMapper pictureShareMapper;

    // 通过 ApplicationContext 获取代理，让 replacePictureFile 的锁包裹整个事务
    @Resource
    private org.springframework.context.ApplicationContext applicationContext;

    private PictureService getSelf() {
        return applicationContext.getBean(PictureService.class);
    }

    private static final long MAX_CHUNK_SIZE = 5L * 1024 * 1024;

    private static final int MAX_CHUNK_COUNT = 6000;

    private void refreshUserSessionState(User user) {
        userService.refreshUserInfoCache(user);
        // 头像变更需要额外清除权限上下文缓存，强制下游重新加载
        stringRedisTemplate.delete(RedisConstants.getUserPermCtxKey(user.getId()));
        cacheManager.getUserPermCache().evict(String.valueOf(user.getId()));
    }

    @Override
    public String uploadAvatar(MultipartFile file, Long id) {
        User userLogin = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(userLogin), ExceptionCode.NOT_LOGIN);
        // 只有自己或管理员可以修改头像
        hk.ljx.fishpicsbackend.common.context.LoginContext ctx = UserHolder.getLoginContext();
        ExcUtils.throwIfTrue(!userLogin.getId().equals(id) && (ctx == null || !ctx.hasSystemPerm("system:user:manage")), "没有权限");
        // 文件类型校验（防止上传恶意文件作为头像）
        ExcUtils.throwIfTrue(FileTypeUtils.getValidFileType(file) == null, ExceptionCode.PARAMETER_ERROR, "不支持的文件类型");
        // 文件大小校验（5MB）
        ExcUtils.throwIfTrue(file.getSize() > 5 * 1024 * 1024, ExceptionCode.PARAMETER_ERROR, "头像大小不能超过5MB");
        User user;
        if (id != null && !id.equals(userLogin.getId())) {
            user = userService.getById(id);
            ExcUtils.throwIfTrue(user == null, "该用户不存在");
        } else {
            user = userService.getById(userLogin.getId());
            ExcUtils.throwIfTrue(user == null, "用户不存在");
        }
        // 先上传新头像
        String url = cosService.uploadAndGetImageUrl(file);
        String oldAvatar = user.getAvatar();
        user.setAvatar(url);
        try {
            ExcUtils.throwIfFalse(userService.updateById(user), ExceptionCode.DATABASE_ERROR, "上传失败，数据库错误");
        } catch (Exception e) {
            // DB 更新失败，回滚 COS 上传
            try {
                cosService.deletePictureByUrl(url);
            } catch (Exception ex) {
                log.warn("COS 回滚新头像失败: {}", url, ex);
            }
            throw e;
        }
        // DB 成功后删除旧头像（非事务性，失败不回滚）
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

    /**
     * 上传图片
     * 流程：权限校验 → 文件类型/大小校验 → MD5 去重（秒传）→ 上传 COS → 空间配额校验 → 写库
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Picture uploadPicture(MultipartFile file, Long targetSpaceId) {
        User userLogin = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(userLogin), "请先登录");
        Long userId = userLogin.getId();
        ExcUtils.throwIfTrue(userId == null, "请先登录");

        // 按用户等级动态限制上传大小
        long maxSize = getMaxUploadSize(userLogin.getLevel());
        ExcUtils.throwIfTrue(file.getSize() > maxSize,
                "上传图片大小不能超过" + formatSize(maxSize));

        // 大文件强制走分片上传（100MB 阈值）
        final long DIRECT_UPLOAD_LIMIT = 100L * 1024 * 1024;
        ExcUtils.throwIfTrue(file.getSize() > DIRECT_UPLOAD_LIMIT,
                "文件超过 100MB,请使用分片上传(前端应自动走 chunked 路径)");

        // 文件类型校验（魔数检测）
        String validFileType = FileTypeUtils.getValidFileType(file);
        ExcUtils.throwIfTrue(validFileType == null, "上传文件格式不正确");

        // ---- file_resource 去重 ----
        String md5;
        try (InputStream is = file.getInputStream()) {
            md5 = cn.hutool.crypto.digest.DigestUtil.md5Hex(is);
        } catch (IOException e) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "计算文件MD5失败");
        }
        long fileSize = file.getSize();
        FileResource existingResource = fileResourceService.findByMd5AndSize(md5, fileSize);

        PictureMessage pictureMessage;
        FileResource resource;
        String cosKey = null;
        boolean isNewUpload = (existingResource == null);
        if (existingResource != null) {
            // 秒传：file_resource 已存在，直接复用，ref_count + 1
            resource = fileResourceService.addResource(md5, fileSize, existingResource.getCosKey());
            pictureMessage = cosService.getPictureMessage(existingResource.getCosKey());
        } else {
            // 新文件：上传 COS → 写 file_resource
            cosKey = cosService.uploadPicture(file);
            pictureMessage = cosService.getPictureMessage(cosKey);
            resource = fileResourceService.addResource(md5, fileSize, cosKey);
        }

        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureMessage, picture);
        picture.setUserId(userId);
        picture.setResourceId(resource.getId());
        // BeanUtil 可能无法将 String size 转为 Long，手动转换兜底
        if (picture.getSize() == null && pictureMessage.getSize() != null) {
            try {
                picture.setSize(Long.parseLong(pictureMessage.getSize()));
            } catch (NumberFormatException e) {
                log.warn("图片大小解析失败: {}", pictureMessage.getSize());
            }
        }
        ExcUtils.throwIfTrue(picture.getSize() == null, ExceptionCode.INTERNAL_SERVER_ERROR, "获取图片大小失败");

        long size = picture.getSize();
        Space space;
        if (targetSpaceId != null) {
            space = spaceService.getById(targetSpaceId);
            ExcUtils.throwIfTrue(space == null, "目标空间不存在");
        } else {
            List<hk.ljx.fishpicsbackend.space.vo.SpaceVO> spaceList = spaceService.listSpace(0);
            ExcUtils.throwIfTrue(spaceList == null || spaceList.isEmpty(), "私人空间不存在，请联系管理员");
            space = spaceService.getById(spaceList.get(0).getId());
        }
        // 校验空间写权限
        checkSpaceWritePermission(space, userId);

        if (!atomicUpdateSpaceSize(space, size, true)) {
            // 配额不足：如果是新上传的 COS 文件，需要清理（与 savePictureByUrl 逻辑一致）
            if (isNewUpload && cosKey != null) {
                try { cosService.deletePicture(cosKey); } catch (Exception ex) { log.warn("COS 回滚失败: {}", cosKey, ex); }
            }
            // 配额不足时,addResource 已 ref_count+1 需回滚
            // (秒传场景下:ref_count 增但没 picture 引用,COS 永不清理)
            if (resource != null) {
                try {
                    fileResourceService.decrementRefCount(resource.getId());
                } catch (Exception ex) {
                    log.warn("ref_count 回滚失败(可能 ref_count=0 已触发 COS 删,幂等可接受): resourceId={}", resource.getId(), ex);
                }
            }
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "空间容量不足");
        }
        picture.setSpaceId(space.getId());
        // 上传到空间直接通过，无需审核
        picture.setStatus(1);
        try {
            ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "上传失败，数据库错误");
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 唯一索引兜底：查重没挡住（并发），回滚后返回已有
            if (isNewUpload && cosKey != null) {
                try { cosService.deletePicture(cosKey); } catch (Exception ex) { log.warn("COS 回滚失败: {}", cosKey, ex); }
            }
            try { atomicUpdateSpaceSize(space, -size, false); } catch (Exception ex) { log.warn("空间配额回滚失败: space={}, size={}", space.getId(), size, ex); }
            if (resource != null) {
                try { fileResourceService.decrementRefCount(resource.getId()); } catch (Exception ex) { log.warn("ref_count 回滚失败: resourceId={}", resource.getId(), ex); }
            }
            Picture existing = pictureMapper.selectOne(new LambdaQueryWrapper<Picture>()
                    .eq(Picture::getResourceId, resource != null ? resource.getId() : 0)
                    .eq(Picture::getUserId, userId)
                    .eq(Picture::getSpaceId, space.getId())
                    .last("LIMIT 1"));
            if (existing != null) return existing;
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "图片已存在");
        } catch (Exception e) {
            // DB insert 失败时，回滚 COS 和空间配额
            if (isNewUpload && cosKey != null) {
                try { cosService.deletePicture(cosKey); } catch (Exception ex) { log.warn("COS 回滚失败: {}", cosKey, ex); }
            }
            try { atomicUpdateSpaceSize(space, -size, false); } catch (Exception ex) { log.warn("空间配额回滚失败: space={}, size={}", space.getId(), size, ex); }
            // 回滚引用计数(与 savePictureByUrl 逻辑一致)
            if (resource != null) {
                try { fileResourceService.decrementRefCount(resource.getId()); } catch (Exception ex) { log.warn("ref_count 回滚失败: resourceId={}", resource.getId(), ex); }
            }
            throw e;
        }
        return picture;
    }

    /**
     * 通过 URL 保存图片
     * 把外部图片下载到临时文件后走和直接上传一样的去重/配额/审核流程
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Picture savePictureByUrl(String url, Long targetSpaceId) {
        User userLogin = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(userLogin), "请先登录");
        Long userId = userLogin.getId();
        ExcUtils.throwIfTrue(userId == null, "请先登录");
        ExcUtils.throwIfTrue(StrUtil.isBlank(url), "图片URL不能为空");

        // 按用户等级动态限制下载大小
        long maxSize = getMaxUploadSize(userLogin.getLevel());

        // 1. 下载到临时文件
        File tempFile = DownloadUtils.download(url, maxSize);
        try {
            // 2. 魔数检测（新开一个流，避免影响后续上传）
            try (FileInputStream fis = new FileInputStream(tempFile)) {
                String fileType = FileTypeUtils.getValidFileType(fis);
                ExcUtils.throwIfTrue(fileType == null, "不支持的图片格式");
            } catch (IOException e) {
                throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "读取临时文件失败");
            }

            // 3. 计算 MD5，检查 file_resource 去重
            String md5 = cn.hutool.crypto.digest.DigestUtil.md5Hex(tempFile);
            long fileSize = tempFile.length();
            FileResource existingResource = fileResourceService.findByMd5AndSize(md5, fileSize);

            PictureMessage pictureMessage;
            FileResource resource;
            String key;
            boolean isNewUpload;
            if (existingResource != null) {
                // 秒传：file_resource 已存在
                resource = fileResourceService.addResource(md5, fileSize, existingResource.getCosKey());
                pictureMessage = cosService.getPictureMessage(existingResource.getCosKey());
                key = existingResource.getCosKey();
                isNewUpload = false;
            } else {
                // 新文件：上传 COS
                try (FileInputStream fis = new FileInputStream(tempFile)) {
                    key = cosService.uploadPicture(fis, tempFile.length());
                } catch (IOException e) {
                    throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "读取临时文件失败");
                }
                pictureMessage = cosService.getPictureMessage(key);
                resource = fileResourceService.addResource(md5, fileSize, key);
                isNewUpload = true;
            }

            // 4. 获取 COS 图片信息
            Picture picture = new Picture();
            BeanUtil.copyProperties(pictureMessage, picture);
            picture.setUserId(userId);
            picture.setResourceId(resource.getId());
            if (picture.getSize() == null && pictureMessage.getSize() != null) {
                try {
                    picture.setSize(Long.parseLong(pictureMessage.getSize()));
                } catch (NumberFormatException e) {
                    log.warn("图片大小解析失败: {}", pictureMessage.getSize());
                }
            }
            ExcUtils.throwIfTrue(picture.getSize() == null, ExceptionCode.INTERNAL_SERVER_ERROR, "获取图片大小失败");

            long size = picture.getSize();
            Space space;
            if (targetSpaceId != null) {
                space = spaceService.getById(targetSpaceId);
                ExcUtils.throwIfTrue(space == null, "目标空间不存在");
            } else {
                List<hk.ljx.fishpicsbackend.space.vo.SpaceVO> spaceList = spaceService.listSpace(0);
                ExcUtils.throwIfTrue(spaceList == null || spaceList.isEmpty(), "私人空间不存在，请联系管理员");
                space = spaceService.getById(spaceList.get(0).getId());
            }
            // 校验空间写权限
            checkSpaceWritePermission(space, userId);

            // 同一用户在同一个空间已经存过同一张图 → 直接返回已有的图片
            if (resource != null) {
                Long existingCount = pictureMapper.selectCount(new LambdaQueryWrapper<Picture>()
                        .eq(Picture::getResourceId, resource.getId())
                        .eq(Picture::getUserId, userId)
                        .eq(Picture::getSpaceId, space.getId()));
                if (existingCount > 0) {
                    Picture existingPic = pictureMapper.selectOne(new LambdaQueryWrapper<Picture>()
                            .eq(Picture::getResourceId, resource.getId())
                            .eq(Picture::getUserId, userId)
                            .eq(Picture::getSpaceId, space.getId())
                            .last("LIMIT 1"));
                    // 回滚 addResource 增加的 ref_count，因为没创建新图片
                    fileResourceService.decrementRefCount(resource.getId());
                    return existingPic;
                }
            }

            // 5. 空间配额检查（使用原子操作防止竞态条件）
            if (!atomicUpdateSpaceSize(space, size, true)) {
                // 仅删除新上传的 COS 文件；去重资源的 COS 文件仍被其他图片引用，不可删除
                if (isNewUpload) {
                    try { cosService.deletePicture(key); } catch (Exception ex) { log.warn("COS 回滚失败: {}", key, ex); }
                }
                // 配额不足时,addResource 已 ref_count+1 需回滚
                if (resource != null) {
                    try {
                        fileResourceService.decrementRefCount(resource.getId());
                    } catch (Exception ex) {
                        log.warn("savePictureByUrl ref_count 回滚失败(配额不足): resourceId={}", resource.getId(), ex);
                    }
                }
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "空间容量不足");
            }
            picture.setSpaceId(space.getId());

            // 上传到空间直接通过，无需审核
            picture.setStatus(1);
            try {
                ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "上传失败，数据库错误");
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 唯一索引 uk_resource_user_space 兜底：
                // 查重在前面没挡住（并发 TOCTOU），回滚后返回已有图片
                atomicUpdateSpaceSize(space, -size, false);
                if (isNewUpload) {
                    try { cosService.deletePicture(key); } catch (Exception ex) { log.warn("COS 回滚失败: {}", key, ex); }
                }
                if (resource != null) {
                    try { fileResourceService.decrementRefCount(resource.getId()); } catch (Exception ex) { log.warn("ref_count 回滚失败: resourceId={}", resource.getId(), ex); }
                }
                Picture existing = pictureMapper.selectOne(new LambdaQueryWrapper<Picture>()
                        .eq(Picture::getResourceId, resource != null ? resource.getId() : 0)
                        .eq(Picture::getUserId, userId)
                        .eq(Picture::getSpaceId, space.getId())
                        .last("LIMIT 1"));
                if (existing != null) return existing;
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "图片已存在");
            } catch (Exception e) {
                // DB 失败：回滚空间配额和 COS 文件
                atomicUpdateSpaceSize(space, -size, false);
                if (isNewUpload) {
                    try { cosService.deletePicture(key); } catch (Exception ex) { log.warn("COS 回滚失败: {}", key, ex); }
                }
                // 秒传场景 addResource 已 ref_count+1，picture 插失败时也要 decrement
                // 之前只有 COS 上传场景走 isNewUpload,秒传(ref 复用)时 ref_count 永远 +1 不会回滚,
                // 导致 file_resource 引用计数虚增,COS 文件永远不清理
                if (resource != null) {
                    try {
                        fileResourceService.decrementRefCount(resource.getId());
                    } catch (Exception ex) {
                        log.warn("savePictureByUrl ref_count 回滚失败(非阻塞): resourceId={}", resource.getId(), ex);
                    }
                }
                throw e;
            }
            return picture;

        } finally {
            // 6. 清理临时文件
            FileUtil.del(tempFile);
        }
    }

    @Override
    public IPage<PictureVO> getPictureList(PictureQueryRequest pictureQueryRequest) {
        LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<Picture>()
                .eq(Picture::getStatus, 1)
                .eq(Picture::getIsPrivate, 0)
                .isNotNull(Picture::getUrl)
                .ne(Picture::getUrl, "")
                .orderByDesc(Picture::getCreateTime);

        // tag 过滤：通过 picture_tag 表子查询
        if (StrUtil.isNotBlank(pictureQueryRequest.getTag())
                && !"热门".equals(pictureQueryRequest.getTag())) {
            List<Long> pictureIdsWithTag = pictureTagMapper.selectList(
                    new LambdaQueryWrapper<hk.ljx.fishpicsbackend.picture.entity.PictureTag>()
                            .like(hk.ljx.fishpicsbackend.picture.entity.PictureTag::getTagName, pictureQueryRequest.getTag())
                            .select(hk.ljx.fishpicsbackend.picture.entity.PictureTag::getPictureId))
                    .stream()
                    .map(hk.ljx.fishpicsbackend.picture.entity.PictureTag::getPictureId)
                    .distinct()
                    .collect(Collectors.toList());
            if (pictureIdsWithTag.isEmpty()) {
                Page<Picture> emptyPage = new Page<>(pictureQueryRequest.getCurrent(), pictureQueryRequest.getPageSize());
                emptyPage.setRecords(Collections.emptyList());
                return emptyPage.convert(p -> PictureVO.ofList(p.getId(), p.getUrl(), Collections.emptyList()));
            }
            queryWrapper.in(Picture::getId, pictureIdsWithTag);
        }

        Page<Picture> page = new Page<>(pictureQueryRequest.getCurrent(), pictureQueryRequest.getPageSize());
        IPage<Picture> picturePage = pictureMapper.selectPage(page, queryWrapper);
        // 批量加载标签
        List<Long> pagePictureIds = picturePage.getRecords().stream().map(Picture::getId).collect(Collectors.toList());
        Map<Long, List<String>> tagsMap = batchLoadTags(pagePictureIds);
        return picturePage.convert(p -> PictureVO.ofList(p.getId(), p.getUrl(),
                tagsMap.getOrDefault(p.getId(), Collections.emptyList())));
    }

    @Override
    public IPage<PictureVO> getAdminPictureList(AdminPictureListDTO dto) {
        LambdaQueryWrapper<Picture> queryWrapper = new LambdaQueryWrapper<Picture>()
                .isNotNull(Picture::getUrl)
                .ne(Picture::getUrl, "")
                .orderByDesc(Picture::getCreateTime);

        Integer status = dto.getStatus();
        if (status != null) {
            if (status == 4) {
                // 4=精选申请（isSelected=1 且 status=1）
                queryWrapper.eq(Picture::getIsPrivate, 0);
            } else if (status == 5) {
                // 5=待精选审核(用户申请了精选|手工改了isSelected=2)
                queryWrapper.eq(Picture::getIsSelected, 2)
                        .in(Picture::getStatus, 0, 1);
            } else {
                queryWrapper.eq(Picture::getStatus, status);
            }
        }

        Page<Picture> page = new Page<>(dto.getCurrent(), dto.getPageSize());
        IPage<Picture> picturePage = pictureMapper.selectPage(page, queryWrapper);
        // 批量加载标签
        List<Long> pagePictureIds = picturePage.getRecords().stream().map(Picture::getId).collect(Collectors.toList());
        Map<Long, List<String>> tagsMap = batchLoadTags(pagePictureIds);
        return picturePage.convert(p -> PictureVO.ofAdmin(
                p.getId(),
                p.getUrl(),
                p.getWidth(),
                p.getHeight(),
                p.getSize(),
                p.getStatus(),
                p.getCreateTime(),
                p.getUserId(),
                p.getIsPrivate(),
                p.getIsSelected(),
                tagsMap.getOrDefault(p.getId(), Collections.emptyList())));
    }

    @Override
    public void reviewPicture(Long pictureId, Integer status, Integer selected) {
        ExcUtils.throwIfTrue(pictureId == null, "图片id不能为空");
        Picture picture = pictureMapper.selectById(pictureId);
        ExcUtils.throwIfTrue(picture == null, "图片不存在");
        // 精选审核：selected=1 通过精选，selected=0 拒绝精选
        if (selected != null) {
            ExcUtils.throwIfTrue((selected != 0 && selected != 1), "精选值无效");
            picture.setIsSelected(selected);
            // 拒绝精选时把申请状态清掉
            if (selected == 0 && Integer.valueOf(2).equals(picture.getIsSelected())) {
                picture.setIsSelected(0);
            }
        }
        ExcUtils.throwIfTrue(pictureMapper.updateById(picture) != 1, "审核更新失败");
    }

    /**
     * 批量删除图片
     * 要做的事：删图片记录 → 回退空间配额 → 递减 file_resource 引用计数
     * 旧数据（没有 resourceId 的图片）在事务提交后单独删 COS 文件，避免回滚后 COS 文件已经没了
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String deletePicture(DeleteByIdList deleteByIdList) {
        List<Long> ids = deleteByIdList.getIds();
        ExcUtils.throwIfTrue(CollUtil.isEmpty(ids), "图片id不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);

        // 批量查询图片
        List<Picture> pictureList = pictureMapper.selectList(new LambdaQueryWrapper<Picture>().in(Picture::getId, ids));
        ExcUtils.throwIfTrue(CollUtil.isEmpty(pictureList), "图片不存在");

        // 按权限过滤出可删的子集
        List<Long> deletableIds = hk.ljx.fishpicsbackend.picture.service.PicturePermissionUtil
                .filterDeletableIds(pictureList, ids, spaceTeamMemberMapper);
        if (deletableIds.isEmpty()) {
                // 已登录但无权删别人图 → FORBIDDEN
                throw new BaseException(ExceptionCode.FORBIDDEN, "没有可删除的图片(可能都不是您有权删除的)");
        }

        // 只删 deletableIds
        int i = pictureMapper.delete(new LambdaQueryWrapper<Picture>().in(Picture::getId, deletableIds));
        ExcUtils.throwIfTrue(i == 0, "删除失败");
        // 过滤 pictureList 保留 deletableIds 内的
        pictureList = pictureList.stream()
                .filter(p -> deletableIds.contains(p.getId()))
                .collect(Collectors.toList());

        // 级联清理 picture_share 记录
        try {
            int shareDelCount = pictureShareMapper.delete(new LambdaQueryWrapper<hk.ljx.fishpicsbackend.picture.entity.PictureShare>()
                    .in(hk.ljx.fishpicsbackend.picture.entity.PictureShare::getPictureId, deletableIds));
            if (shareDelCount > 0) {
                log.info("级联删除 picture_share: count={}", shareDelCount);
            }
        } catch (Exception e) {
            log.warn("清理 picture_share 失败(非阻塞): {}", e.getMessage());
        }

        // 级联清理 picture_tag 记录
        try {
            int tagDelCount = pictureTagMapper.delete(new LambdaQueryWrapper<hk.ljx.fishpicsbackend.picture.entity.PictureTag>()
                    .in(hk.ljx.fishpicsbackend.picture.entity.PictureTag::getPictureId, deletableIds));
            if (tagDelCount > 0) {
                log.info("级联删除 picture_tag: count={}", tagDelCount);
            }
        } catch (Exception e) {
            log.warn("清理 picture_tag 失败(非阻塞): {}", e.getMessage());
        }

        // 扣减空间已用大小
        Map<Long, Long> spaceSizeMap = new java.util.HashMap<>();
        pictureList.forEach(picture -> {
            if (picture.getSpaceId() != null && picture.getSize() != null) {
                spaceSizeMap.merge(picture.getSpaceId(), picture.getSize(), Long::sum);
            } else {
                log.warn("图片 spaceId 或 size 为空，无法回退空间配额: pictureId={}, spaceId={}, size={}",
                        picture.getId(), picture.getSpaceId(), picture.getSize());
            }
        });
        spaceSizeMap.forEach((spaceId, deletedSize) -> {
            Space space = spaceService.getById(spaceId);
            if (space != null) {
                atomicUpdateSpaceSize(space, -deletedSize, false);
            }
        });

        // file_resource 引用计数递减，ref_count 归零时删除 COS 文件（在事务提交后执行）
        List<String> legacyCosUrls = new java.util.ArrayList<>();
        for (Picture picture : pictureList) {
            if (picture.getResourceId() != null) {
                try {
                    int newCount = fileResourceService.decrementRefCount(picture.getResourceId());
                    if (newCount == 0) {
                        log.info("file_resource 引用归零，已删除物理文件: resourceId={}", picture.getResourceId());
                    }
                } catch (Exception e) {
                    log.warn("清理图片资源引用失败: pictureId={}, resourceId={}", picture.getId(), picture.getResourceId(), e);
                }
            } else {
                legacyCosUrls.add(picture.getUrl());
            }
        }
        if (!legacyCosUrls.isEmpty()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            for (String url : legacyCosUrls) {
                                try {
                                    cosService.deletePictureByUrl(url);
                                } catch (Exception e) {
                                    log.error("COS 文件删除失败: url={}", url, e);
                                }
                            }
                        }
                    });
        }
        return "删除成功";
    }

    @Override
    public void updatePicture(PictureUpdateRequest request) {
        Long id = request.getId();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(id), "图片id不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null || user.getId() == null, ExceptionCode.UNAUTHORIZED, "用户未登录");
        Picture picture = pictureMapper.selectById(id);
        ExcUtils.throwIfTrue(picture == null || picture.getUserId() == null, ExceptionCode.NOT_FOUND, "图片不存在");
        // 统一走 PicturePermissionUtil
        hk.ljx.fishpicsbackend.picture.service.PicturePermissionUtil.checkWrite(
                picture, hk.ljx.fishpicsbackend.picture.service.PicturePermissionUtil.Op.EDIT_META,
                spaceTeamMemberMapper);

        // updatePicture 不允许改 url（URL 变更走 replace 接口）
        request.setUrl(null);
        Long pictureId = request.getId();
        Integer isSelected = request.getIsSelected();
        if (isSelected != null) {
            // 用户申请精选 → isSelected=2（待审核）
            // 只有图片所属空间的管理员或创建者可以申请精选
            hk.ljx.fishpicsbackend.picture.service.PicturePermissionUtil.checkWrite(
                    picture, hk.ljx.fishpicsbackend.picture.service.PicturePermissionUtil.Op.EDIT_META,
                    spaceTeamMemberMapper);
            if (isSelected == 1) {
                ExcUtils.throwIfTrue(picture.getIsSelected() != null && picture.getIsSelected() == 1,
                        ExceptionCode.PARAMETER_ERROR, "该图片已经是精选");
                picture.setIsSelected(2);
            } else if (isSelected == 0) {
                picture.setIsSelected(0);
            }
            pictureMapper.updateById(picture);
            return;
        }

        String pictureName = request.getPictureName();
        String introduction = request.getIntroduction();
        List<String> tags = request.getTags();

        // pictureName / introduction 入库前清 HTML 标签
        if (ObjUtil.isNotEmpty(pictureName)) {
            String cleanName = hk.ljx.fishpicsbackend.common.utils.XssSanitizer.clean(pictureName);
            // 清洗后校验长度
            ExcUtils.throwIfTrue(cleanName.length() > 100, ExceptionCode.PARAMETER_ERROR, "图片名称过长(最多 100 字符)");
            picture.setPictureName(cleanName);
        }
        if (ObjUtil.isNotEmpty(introduction)) {
            String cleanIntro = hk.ljx.fishpicsbackend.common.utils.XssSanitizer.cleanRelaxed(introduction);
            ExcUtils.throwIfTrue(cleanIntro.length() > 500, ExceptionCode.PARAMETER_ERROR, "图片描述过长(最多 500 字符)");
            picture.setIntroduction(cleanIntro);
        }
        if (ObjUtil.isNotEmpty(tags)) {
            // tags 数量上限 10
            ExcUtils.throwIfTrue(tags.size() > 10, ExceptionCode.PARAMETER_ERROR, "标签数量不能超过 10 个");
            List<String> typeList = picSystemService.getTypeList();
            Set<String> typeSet = new java.util.HashSet<>(typeList);
            // tags 也清理一遍
            List<String> safeTags = tags.stream()
                    .map(hk.ljx.fishpicsbackend.common.utils.XssSanitizer::clean)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());
            boolean result = safeTags.stream().anyMatch(tag -> !typeSet.contains(tag));
            ExcUtils.throwIfTrue(result, ExceptionCode.PARAMETER_ERROR, "标签不存在");
            replacePictureTags(pictureId, safeTags);
        }

        // 之前 request.setUrl(null) 已堵掉 url 变更，URL 替换的合法路径是 replacePictureFile
        int i = pictureMapper.updateById(picture);
        ExcUtils.throwIfFalse(i > 0, ExceptionCode.INTERNAL_SERVER_ERROR, "更新失败");
    }

    /**
     * 协同编辑替换图片文件：上传新文件到 COS → 更新记录 → 清理旧文件
     * 锁在事务外获取/释放，确保并发替换不会在事务提交前释放锁
     */
    @Override
    public PictureVO replacePictureFile(Long pictureId, MultipartFile file) {
        // 1. 校验
        ExcUtils.throwIfTrue(pictureId == null, "图片ID不能为空");
        ExcUtils.throwIfTrue(file == null || file.isEmpty(), "文件不能为空");

        // 并发 replacePictureFile 时的分布式锁防止孤立 file_resource 和配额重复计算
        String lockKey = "lock:replace-picture:" + pictureId;
        if (!distributedLockService.tryLock(lockKey, 30)) {
            throw new BaseException(ExceptionCode.CONFLICT, "该图片正在被替换,请稍后重试");
        }
        try {
            return getSelf().doReplacePictureFile(pictureId, file);
        } finally {
            distributedLockService.unlock(lockKey);
        }
    }

    /**
     * 事务方法独立为 public（通过 self 代理调用），锁在调用方获取/释放
     */
    @Transactional(rollbackFor = Exception.class)
    public PictureVO doReplacePictureFile(Long pictureId, MultipartFile file) {
        // 文件类型校验（与 uploadPicture 等一致）
        ExcUtils.throwIfTrue(hk.ljx.fishpicsbackend.common.utils.FileTypeUtils.getValidFileType(file) == null,
                ExceptionCode.PARAMETER_ERROR, "不支持的文件类型");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null || user.getId() == null, ExceptionCode.UNAUTHORIZED, "用户未登录");

        Picture picture = pictureMapper.selectById(pictureId);
        ExcUtils.throwIfTrue(picture == null || picture.getUserId() == null, ExceptionCode.NOT_FOUND, "图片不存在");

        // 拒绝编辑处于禁用/待审核状态的图片
        if (picture.getStatus() != null && !Integer.valueOf(1).equals(picture.getStatus())) {
            // 管理员可以强制替换
            hk.ljx.fishpicsbackend.common.context.LoginContext ctxForStatus = UserHolder.getLoginContext();
            boolean isAdminForStatus = ctxForStatus != null && ctxForStatus.hasSystemPerm("system:user:manage");
            if (!isAdminForStatus) {
                throw new BaseException(ExceptionCode.FORBIDDEN, "图片当前状态不可编辑");
            }
        }

        // 权限：图片所有者 / 管理员 / 团队成员
        hk.ljx.fishpicsbackend.common.context.LoginContext ctx = UserHolder.getLoginContext();
        boolean isOwner = picture.getUserId().equals(user.getId());
        boolean isAdmin = ctx != null && ctx.hasSystemPerm("system:user:manage");
        boolean isTeamMember = !isOwner && !isAdmin && spaceTeamMemberMapper.selectCount(
                new LambdaQueryWrapper<hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember>()
                        .eq(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getSpaceId, picture.getSpaceId())
                        .eq(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getUserId, user.getId())
                        .in(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getRoleId, List.of(1, 2))
        ) > 0;
        ExcUtils.throwIfFalse(isOwner || isAdmin || isTeamMember,
                // 已认证但不是 owner/team member → FORBIDDEN
                ExceptionCode.FORBIDDEN, "没有权限编辑图片");

        // 2. 计算 MD5（必须在 COS 上传之前，因为 InputStream 只能读一次）
        String md5;
        try (InputStream is = file.getInputStream()) {
            md5 = cn.hutool.crypto.digest.DigestUtil.md5Hex(is);
        } catch (IOException e) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "计算文件MD5失败");
        }

        // 2.1 内容未变短路：如果 MD5+size 与旧 resource 完全相同，跳过替换
        Long oldResourceId = picture.getResourceId();
        FileResource existingByMd5 = fileResourceService.findByMd5AndSize(md5, file.getSize());
        if (existingByMd5 != null && oldResourceId != null && existingByMd5.getId().equals(oldResourceId)) {
            log.info("协同编辑：文件内容未变，跳过替换 pictureId={}", pictureId);
            PictureVO vo = new PictureVO();
            vo.setUrl(picture.getUrl());
            vo.setUpdateTime(picture.getUpdateTime());
            return vo;
        }

        // 3. MD5 去重：命中则复用 COS 文件，未命中则上传
        String newCosKey;
        if (existingByMd5 != null) {
            newCosKey = existingByMd5.getCosKey();
            log.info("协同编辑：MD5 命中秒传 pictureId={}, cosKey={}", pictureId, newCosKey);
        } else {
            newCosKey = cosService.uploadPicture(file);
        }
        PictureMessage pictureMessage = cosService.getPictureMessage(newCosKey);

        // 4. 创建 file_resource 记录
        FileResource newResource = fileResourceService.addResource(md5, file.getSize(), newCosKey);

        // 5. 更新 picture 记录
        String oldUrl = picture.getUrl();
        long oldSize = picture.getSize() != null ? picture.getSize() : 0;
        long newSize = newResource.getSize();

        // 5.1 空间配额检查(仅当新文件更大时)
        long sizeDiff = newSize - oldSize;
        if (sizeDiff > 0) {
            Space space = spaceService.getById(picture.getSpaceId());
            if (space != null) {
                // 用 SQL 原子 UPDATE 抢占配额
                int updated = spaceMapper.conditionalIncrementSize(space.getId(), sizeDiff);
                if (updated == 0) {
                    if (existingByMd5 == null) {
                        try { cosService.deletePicture(newCosKey); } catch (Exception ex) { log.warn("COS 回滚失败: {}", newCosKey, ex); }
                    }
                    // addResource 已 ref_count+1,配额不足需回滚
                    if (newResource != null) {
                        try {
                            fileResourceService.decrementRefCount(newResource.getId());
                        } catch (Exception ex) {
                            log.warn("replace ref_count 回滚失败: resourceId={}", newResource.getId(), ex);
                        }
                    }
                    throw new BaseException(ExceptionCode.PARAMETER_ERROR, "空间容量不足,无法保存");
                }
                // 配额已原子 +sizeDiff,后续 atomicUpdateSpaceSize 不能再 +1
                sizeDiff = 0;
                log.info("[replace] 配额原子抢占成功: space={}, +{} bytes", space.getId(), newSize - oldSize);
            }
        }

        picture.setUrl(pictureMessage.getUrl());
        picture.setResourceId(newResource.getId());
        picture.setSize(newSize);
        if (pictureMessage.getPictureName() != null) {
            picture.setPictureName(pictureMessage.getPictureName());
        }
        ExcUtils.throwIfTrue(pictureMapper.updateById(picture) != 1, ExceptionCode.INTERNAL_SERVER_ERROR, "更新失败");

        // 6. 旧 file_resource 引用计数递减（归零时自动删除 COS 文件）
        if (oldResourceId != null) {
            try {
                int newCount = fileResourceService.decrementRefCount(oldResourceId);
                if (newCount == 0) {
                    log.info("协同编辑：旧文件引用归零，已删除: resourceId={}", oldResourceId);
                }
            } catch (Exception e) {
                log.warn("协同编辑：旧文件引用递减失败: resourceId={}", oldResourceId, e);
            }
        }

        // 7. 调整空间已用大小
        if (sizeDiff != 0) {
            Space space = spaceService.getById(picture.getSpaceId());
            if (space != null) {
                atomicUpdateSpaceSize(space, sizeDiff, false);
            }
        }

        log.info("协同编辑：图片文件已替换 pictureId={}, oldUrl={}, newUrl={}", pictureId, oldUrl, picture.getUrl());

        // 服务端主动推送 file-replaced 事件（在事务提交后）
        // 通过 TransactionSynchronization 保证只在 DB 真正落库后再推,避免回滚导致幽灵事件
        if (picture.getSpaceId() != null) {
            final Long spaceId = picture.getSpaceId();
            final Long finalPictureId = pictureId;
            final Long finalUserId = user.getId();
            final String finalNickname = user.getNickname();
            try {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                collabEventPublisher.publish(event -> {
                                    event.setType(CollabEvent.TYPE_FILE_REPLACED);
                                    event.setPictureId(finalPictureId);
                                    event.setSpaceId(spaceId);
                                    event.setUserId(finalUserId);
                                    event.setNickname(finalNickname);
                                });
                            } catch (Exception e) {
                                log.warn("[replacePictureFile] 推送 file-replaced 事件失败: pictureId={}", finalPictureId, e);
                            }
                        }
                    });
            } catch (Exception e) {
                log.warn("[replacePictureFile] 注册事务同步器失败,跳过 WS 推送: pictureId={}", pictureId, e);
            }
        }

        // 8. 返回更新后的信息（供前端 URL 版本化）
        PictureVO result = new PictureVO();
        result.setUrl(picture.getUrl());
        // 重新查询获取 updateTime（updateById 不会自动填充）
        Picture updated = pictureMapper.selectById(pictureId);
        if (updated != null) {
            result.setUpdateTime(updated.getUpdateTime());
        }
        return result;
    }

    /**
     * 编辑时图片信息回填
     *
     * @param id 图片id
     * @return 图片信息
     */
    @Override
    public PictureVO getPictureEditMessage(Long id) {
        ExcUtils.throwIfTrue(id == null, "图片id不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null || user.getId() == null, ExceptionCode.NOT_LOGIN);
        Picture picture = pictureMapper.selectById(id);
        ExcUtils.throwIfTrue(picture == null || picture.getUserId() == null, ExceptionCode.NOT_FOUND, "图片不存在");
        // 图片所有者、管理员、团队成员均可查看编辑信息（与 updatePicture 权限一致）
        hk.ljx.fishpicsbackend.picture.service.PicturePermissionUtil.checkWrite(
                picture, hk.ljx.fishpicsbackend.picture.service.PicturePermissionUtil.Op.EDIT_META,
                spaceTeamMemberMapper);

        List<String> tagList = loadTagsForPicture(picture.getId());

        return PictureVO.ofDetail(
                picture.getId(),
                picture.getUrl(),
                picture.getPictureName(),
                picture.getIntroduction(),
                tagList
        );
    }

    @Override
    public IPage<PictureVO> getRecommendPictures(PageRequest pageRequest, Long userId) {
        // 复用 getPictureList（不传 tag，即返回全部公开图片）
        PictureQueryRequest query = new PictureQueryRequest();
        query.setCurrent(pageRequest.getCurrent());
        query.setPageSize(pageRequest.getPageSize());
        return getPictureList(query);
    }

    // ==================== 分片上传方法 ====================

    // ==================== 标签辅助方法 ====================

    private List<String> loadTagsForPicture(Long pictureId) {
        return pictureTagMapper.selectList(
                new LambdaQueryWrapper<hk.ljx.fishpicsbackend.picture.entity.PictureTag>()
                        .eq(hk.ljx.fishpicsbackend.picture.entity.PictureTag::getPictureId, pictureId))
                .stream()
                .map(hk.ljx.fishpicsbackend.picture.entity.PictureTag::getTagName)
                .collect(Collectors.toList());
    }

    private Map<Long, List<String>> batchLoadTags(List<Long> pictureIds) {
        if (pictureIds == null || pictureIds.isEmpty()) return Collections.emptyMap();
        return pictureTagMapper.selectList(
                new LambdaQueryWrapper<hk.ljx.fishpicsbackend.picture.entity.PictureTag>()
                        .in(hk.ljx.fishpicsbackend.picture.entity.PictureTag::getPictureId, pictureIds))
                .stream()
                .collect(Collectors.groupingBy(
                        hk.ljx.fishpicsbackend.picture.entity.PictureTag::getPictureId,
                        Collectors.mapping(hk.ljx.fishpicsbackend.picture.entity.PictureTag::getTagName, Collectors.toList())));
    }

    private void replacePictureTags(Long pictureId, List<String> newTags) {
        pictureTagMapper.delete(
                new LambdaQueryWrapper<hk.ljx.fishpicsbackend.picture.entity.PictureTag>()
                        .eq(hk.ljx.fishpicsbackend.picture.entity.PictureTag::getPictureId, pictureId));
        for (String tag : newTags) {
            hk.ljx.fishpicsbackend.picture.entity.PictureTag pt = new hk.ljx.fishpicsbackend.picture.entity.PictureTag();
            pt.setPictureId(pictureId);
            pt.setTagName(tag);
            pictureTagMapper.insert(pt);
        }
    }

    /**
     * 秒传校验
     * 三路判断：
     * 1. file_resource 表有记录 → 秒传命中，直接复用
     * 2. Redis 有分片上传状态 → 断点续传，返回已上传的分片列表
     * 3. 都没有 → 新文件，生成 cosKey 开始上传
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object checkUpload(CheckUploadRequest request) {
        ExcUtils.throwIfTrue(StrUtil.isBlank(request.getMd5()), "MD5 不能为空");
        ExcUtils.throwIfTrue(request.getSize() == null, "文件大小不能为空");

        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(user) || user.getId() == null, ExceptionCode.NOT_LOGIN);
        // 用 userId-scoped key 确保上传会话绑定到当前用户
        bindUploadOwner(request.getMd5(), user.getId());

        long maxSize = getMaxUploadSize(user.getLevel());
        ExcUtils.throwIfTrue(request.getSize() > maxSize,
                "文件大小超过限制（最大" + formatSize(maxSize) + "）");

        // 1. 查 file_resource 表（秒传）
        FileResource resource = fileResourceService.findByMd5AndSize(request.getMd5(), request.getSize());
        if (resource != null) {
            // 幂等检查：同一用户在同一空间对同一资源是否已创建过图片
            Long targetSpaceId = request.getTargetSpaceId();
            Space existingSpace = resolveTargetSpace(targetSpaceId, user.getId());
            Long existCount = pictureMapper.selectCount(new LambdaQueryWrapper<Picture>()
                    .eq(Picture::getResourceId, resource.getId())
                    .eq(Picture::getUserId, user.getId())
                    .eq(Picture::getSpaceId, existingSpace.getId()));
            if (existCount > 0) {
                Picture existingPic = pictureMapper.selectOne(new LambdaQueryWrapper<Picture>()
                        .eq(Picture::getResourceId, resource.getId())
                        .eq(Picture::getUserId, user.getId())
                        .eq(Picture::getSpaceId, existingSpace.getId())
                        .last("LIMIT 1"));
                Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("status", "duplicate");
                result.put("picture", PictureVO.ofUpload(existingPic.getId(), existingPic.getUrl()));
                return result;
            }

            // 秒传命中：增加引用计数，检查返回值（-1 表示记录已被删除）
            int refResult = fileResourceService.incrementRefCount(resource.getId());
            ExcUtils.throwIfTrue(refResult == -1, ExceptionCode.DATABASE_ERROR, "文件资源不存在，请重新上传");

            Picture picture = createPictureFromResource(resource, user.getId(), request.getTargetSpaceId());
            PictureVO vo = PictureVO.ofUpload(picture.getId(), picture.getUrl());

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("status", "duplicate");
            result.put("picture", vo);
            return result;
        }

        // 2. 查 Redis 分片上传状态（断点续传）
        // 用 userId-scoped key 防止不同用户同 MD5 文件的分片数据互相污染
        String chunksKey = RedisConstants.getFileUploadChunksKey(user.getId(), request.getMd5());
        Long chunkCount = stringRedisTemplate.opsForSet().size(chunksKey);
        if (chunkCount != null && chunkCount > 0) {
            String uploadIdKey = RedisConstants.getFileUploadIdKey(user.getId(), request.getMd5());
            String uploadId = stringRedisTemplate.opsForValue().get(uploadIdKey);
            Set<String> uploadedChunks = stringRedisTemplate.opsForSet().members(chunksKey);

            // 恢复原始 cosKey，而非重新生成
            String cosKeyKey = RedisConstants.getFileCosKeyKey(user.getId(), request.getMd5());
            String cosKey = stringRedisTemplate.opsForValue().get(cosKeyKey);
            if (StrUtil.isBlank(cosKey)) {
                // 兜底：如果 cosKey 丢失（极端情况），重新生成
                cosKey = cosService.generateKey();
                stringRedisTemplate.opsForValue().set(cosKeyKey, cosKey,
                        RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
            }

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("status", "resume");
            result.put("uploadedChunks", uploadedChunks != null ?
                    uploadedChunks.stream().map(Integer::parseInt).sorted().toList() : List.of());
            result.put("uploadId", uploadId);
            result.put("cosKey", cosKey);
            refreshUploadSessionTtl(request.getMd5(), user.getId());
            return result;
        }

        // 3. 新文件
        String cosKey = cosService.generateKey();
        // 将 cosKey 存入 Redis，供断点续传时恢复
        String cosKeyKey = RedisConstants.getFileCosKeyKey(user.getId(), request.getMd5());
        stringRedisTemplate.opsForValue().set(cosKeyKey, cosKey,
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        stringRedisTemplate.delete(RedisConstants.getFileMergeResultKey(request.getMd5()));
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("status", "new");
        result.put("cosKey", cosKey);
        refreshUploadSessionTtl(request.getMd5(), user.getId());
        return result;
    }

    @Override
    public Object uploadChunk(MultipartFile file, String md5, Integer chunkIndex) {
        ExcUtils.throwIfTrue(file == null || file.isEmpty(), "分片文件不能为空");
        ExcUtils.throwIfTrue(StrUtil.isBlank(md5), "MD5 不能为空");
        ExcUtils.throwIfTrue(chunkIndex == null || chunkIndex < 0, "分片编号无效");
        ExcUtils.throwIfTrue(file.getSize() > MAX_CHUNK_SIZE,
                ExceptionCode.PARAMETER_ERROR, "单个分片不能超过" + formatSize(MAX_CHUNK_SIZE));
        ExcUtils.throwIfTrue(chunkIndex >= MAX_CHUNK_COUNT,
                ExceptionCode.PARAMETER_ERROR, "分片数量超过限制");

        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(user) || user.getId() == null, ExceptionCode.NOT_LOGIN);
        validateUploadOwner(md5, user.getId());

        // 从 Redis 获取 cosKey（不信任客户端传入，防止越权覆盖他人文件）
        // 用 userId-scoped key
        String cosKeyKey = RedisConstants.getFileCosKeyKey(user.getId(), md5);
        String cosKey = stringRedisTemplate.opsForValue().get(cosKeyKey);
        ExcUtils.throwIfTrue(StrUtil.isBlank(cosKey), "上传会话已过期，请重新上传");

        // 幂等检查：如果该分片已上传，直接返回
        String chunksKey = RedisConstants.getFileUploadChunksKey(user.getId(), md5);
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(chunksKey, String.valueOf(chunkIndex));
        if (Boolean.TRUE.equals(isMember)) {
            String etagKey = RedisConstants.getFileChunkEtagKey(user.getId(), md5);
            Object existingEtagObj = stringRedisTemplate.opsForHash().get(etagKey, String.valueOf(chunkIndex));
            String existingEtag = existingEtagObj != null ? existingEtagObj.toString() : "";
            refreshUploadSessionTtl(md5, user.getId());

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("etag", existingEtag);
            result.put("chunkIndex", chunkIndex);
            return result;
        }

        // 原子 setIfAbsentOrGet 防止并发请求都 initiateMultipartUpload
        String uploadIdKey = RedisConstants.getFileUploadIdKey(user.getId(), md5);
        String uploadId = stringRedisTemplate.opsForValue().get(uploadIdKey);
        if (StrUtil.isBlank(uploadId)) {
            // 用 Lua 原子抢占:第一个请求拿到新 uploadId 并写入,后续请求拿到已存在的
            String newUploadId = cosService.initiateMultipartUpload(cosKey);
            uploadId = redisAtomicOps.setIfAbsentOrGet(uploadIdKey, newUploadId,
                    RedisConstants.FILE_UPLOAD_TTL * 3600);
            // 注:如果 newUploadId 跟拿回的不一致(说明另一个请求先写了),
            // 我们的 newUploadId 实际上没用,但 COS 端会产生一个孤儿 multipart session
            // (会在 24h 内自动清理,不做 abort 避免额外 COS API 调用)
            if (!newUploadId.equals(uploadId)) {
                log.warn("[uploadChunk] uploadId 抢占冲突: cosKey={}, 我的={}, 实际={}",
                        cosKey, newUploadId, uploadId);
            }
        }

        // 上传分片到 COS（分片编号从 1 开始）
        String etag;
        try (InputStream is = file.getInputStream()) {
            InputStream uploadStream = is;
            // 第一个分片做魔数检测，防止通过分片上传路径绕过文件类型校验
            if (chunkIndex == 0) {
                byte[] header = new byte[16];
                int read = is.read(header);
                if (read > 0) {
                    String fileType = FileTypeUtils.getValidFileType(
                            new java.io.ByteArrayInputStream(header, 0, read));
                    ExcUtils.throwIfTrue(fileType == null, ExceptionCode.PARAMETER_ERROR, "不支持的图片格式");
                }
                // 把已读取的头部字节和剩余流拼接后上传
                uploadStream = new java.io.SequenceInputStream(
                        new java.io.ByteArrayInputStream(header, 0, Math.max(read, 0)), is);
            }
            etag = cosService.uploadPart(cosKey, uploadId, chunkIndex + 1, uploadStream, file.getSize());
        } catch (IOException e) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "分片上传失败");
        }

        // 记录到 Redis
        stringRedisTemplate.opsForSet().add(chunksKey, String.valueOf(chunkIndex));
        stringRedisTemplate.expire(chunksKey, RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);

        String etagKey = RedisConstants.getFileChunkEtagKey(user.getId(), md5);
        stringRedisTemplate.opsForHash().put(etagKey, String.valueOf(chunkIndex), etag);
        stringRedisTemplate.expire(etagKey, RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        String chunkSizeKey = RedisConstants.getFileChunkSizeKey(user.getId(), md5);
        stringRedisTemplate.opsForHash().put(chunkSizeKey, String.valueOf(chunkIndex), String.valueOf(file.getSize()));
        stringRedisTemplate.expire(chunkSizeKey, RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        refreshUploadSessionTtl(md5, user.getId());

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("etag", etag);
        result.put("chunkIndex", chunkIndex);
        return result;
    }

    /**
     * 合并分片
     * 校验所有分片已上传 → 空间配额检查 → 创建 picture/file_resource 记录 → 事务提交后合并 COS 分片
     * COS 合并放在事务提交后是为了避免事务回滚时 COS 上留下孤儿文件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PictureVO mergeChunks(MergeChunksRequest request) {
        ExcUtils.throwIfTrue(StrUtil.isBlank(request.getMd5()), "MD5 不能为空");
        ExcUtils.throwIfTrue(request.getSize() == null, "文件大小不能为空");
        // cosKey 客户端参数不再使用，从 Redis 读 storedCosKey
        ExcUtils.throwIfTrue(request.getTotalChunks() == null || request.getTotalChunks() <= 0, "分片总数无效");
        ExcUtils.throwIfTrue(request.getTotalChunks() > MAX_CHUNK_COUNT,
                ExceptionCode.PARAMETER_ERROR, "分片数量超过限制");

        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(user) || user.getId() == null, ExceptionCode.NOT_LOGIN);
        long maxSize = getMaxUploadSize(user.getLevel());
        Map<String, Object> mergeResult = getMergeResult(request.getMd5());
        if (mergeResult != null) {
            validateMergeResultOwner(mergeResult, user.getId());
            String mergedCosKey = getMergeResultValue(mergeResult, "cosKey");
            if (StrUtil.isNotBlank(mergedCosKey) && isMergedObjectAvailable(mergedCosKey)) {
                PictureVO result = buildPictureVOFromMergeResult(mergeResult);
                cleanupUploadSession(request.getMd5(), true, user.getId());
                return result;
            }
            // 幂等兜底：COS 合并未完成但 picture 已入库（afterCommit 失败 + 客户端重试）
            String existingPictureId = getMergeResultValue(mergeResult, "pictureId");
            if (StrUtil.isNotBlank(existingPictureId)) {
                Picture existingPicture = pictureMapper.selectById(Long.parseLong(existingPictureId));
                if (existingPicture != null) {
                    Map<String, String> restoredData = new HashMap<>();
                    restoredData.put("pictureId", String.valueOf(existingPicture.getId()));
                    restoredData.put("url", existingPicture.getUrl());
                    restoredData.put("userId", String.valueOf(existingPicture.getUserId()));
                    restoredData.put("cosKey", mergedCosKey != null ? mergedCosKey : "");
                    restoredData.put("size", String.valueOf(existingPicture.getSize()));
                    stringRedisTemplate.opsForValue().set(
                            RedisConstants.getFileMergeResultKey(request.getMd5()),
                            JSONUtil.toJsonStr(restoredData), RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
                    PictureVO result = buildPictureVOFromMergeResult(mergeResult);
                    cleanupUploadSession(request.getMd5(), true, user.getId());
                    return result;
                }
            }
        }
        validateUploadOwner(request.getMd5(), user.getId());
        ExcUtils.throwIfTrue(request.getSize() > maxSize,
                ExceptionCode.PARAMETER_ERROR, "文件大小超过限制（最大" + formatSize(maxSize) + "）");

        // 1. 完整性校验
        String chunksKey = RedisConstants.getFileUploadChunksKey(user.getId(), request.getMd5());
        Long uploadedCount = stringRedisTemplate.opsForSet().size(chunksKey);
        ExcUtils.throwIfTrue(uploadedCount == null || uploadedCount != request.getTotalChunks().longValue(),
                ExceptionCode.PARAMETER_ERROR, "分片不完整，已上传 " + uploadedCount + "/" + request.getTotalChunks());

        // 2. 获取 uploadId + 所有 ETag（按 chunkIndex 排序）
        String uploadIdKey = RedisConstants.getFileUploadIdKey(user.getId(), request.getMd5());
        String uploadId = stringRedisTemplate.opsForValue().get(uploadIdKey);
        ExcUtils.throwIfTrue(StrUtil.isBlank(uploadId), ExceptionCode.PARAMETER_ERROR, "uploadId 不存在");

        String etagKey = RedisConstants.getFileChunkEtagKey(user.getId(), request.getMd5());
        Map<Object, Object> etagMap = stringRedisTemplate.opsForHash().entries(etagKey);
        ExcUtils.throwIfTrue(etagMap.size() != request.getTotalChunks(),
                ExceptionCode.PARAMETER_ERROR, "分片 ETag 不完整，请重试合并");

        List<PartETag> partETags = etagMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(
                        Integer.parseInt(a.getKey().toString()),
                        Integer.parseInt(b.getKey().toString())))
                .map(entry -> new PartETag(
                        Integer.parseInt(entry.getKey().toString()) + 1,
                        entry.getValue().toString()))
                .collect(Collectors.toList());

        // 3. 先检查空间配额（在 COS 合并之前，避免合并后配额不足需要回滚 COS）
        Space space = resolveTargetSpace(request.getTargetSpaceId(), user.getId());
        // 校验空间写权限
        checkSpaceWritePermission(space, user.getId());
        long size = sumUploadedChunkSizes(user.getId(), request.getMd5(), request.getTotalChunks());
        ExcUtils.throwIfTrue(size > maxSize,
                ExceptionCode.PARAMETER_ERROR, "file size exceeds limit");
        ExcUtils.throwIfFalse(mergeResult != null || atomicUpdateSpaceSize(space, size, true),
                ExceptionCode.PARAMETER_ERROR, "空间容量不足");

        // 4. COS 合并移到事务提交后执行（避免事务回滚时 COS 文件成为孤儿）
        //    使用 Redis 中存储的 cosKey，不信任客户端传入的值（防越权覆盖）
        String storedCosKey = stringRedisTemplate.opsForValue().get(RedisConstants.getFileCosKeyKey(user.getId(), request.getMd5()));
        ExcUtils.throwIfTrue(StrUtil.isBlank(storedCosKey), ExceptionCode.PARAMETER_ERROR, "上传会话已过期，请重新上传");
        final String finalCosKey = storedCosKey;
        final String finalUploadId = uploadId;
        final List<PartETag> finalPartETags = partETags;
        final String finalChunksKey = chunksKey;
        final String finalUploadIdKey = uploadIdKey;
        final String finalEtagKey = etagKey;
        final String finalMd5 = request.getMd5();
        final String finalMergeResultKey = RedisConstants.getFileMergeResultKey(request.getMd5());
        final Long finalUserId = user.getId();
        // 捕获 space 和 size，用于 COS 合并失败时归还配额
        final Space finalSpace = space;
        final long finalSize = size;

        // 5. 构造图片记录（不调用 getPictureMessage，因为 COS 尚未合并完成，对象不可读）
        //    url 和 name 从 cosKey 推导；width/height 在 COS 合并后异步获取
        long actualSize = size;

        // 6. 创建 file_resource 记录
        FileResource resource = fileResourceService.addResource(request.getMd5(), actualSize, finalCosKey);

        // 7. 创建 picture 记录
        Picture picture = new Picture();
        picture.setUrl(cosService.getImageUrl(finalCosKey));
        String[] keyParts = finalCosKey.split("/");
        String fileName = keyParts[keyParts.length - 1];
        String[] nameParts = fileName.split("\\.");
        picture.setPictureName(nameParts[0]);
        picture.setUserId(user.getId());
        picture.setSize(actualSize);
        picture.setSpaceId(space.getId());
        picture.setResourceId(resource.getId());

        hk.ljx.fishpicsbackend.common.context.LoginContext ctx = UserHolder.getLoginContext();
        if (ctx != null && ctx.hasSystemPerm("system:user:manage")) {
            picture.setStatus(1);
        } else {
            picture.setStatus(2);
        }
        ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "保存图片失败");

        // 8. 存储合并结果(用于幂等重试:下次 mergeChunks 可直接返回已创建的 picture)
        // Redis 写用 try/catch 吞异常，防止 Redis 故障导致 picture insert 回滚
        Map<String, String> mergeResultData = new HashMap<>();
        mergeResultData.put("pictureId", String.valueOf(picture.getId()));
        mergeResultData.put("url", picture.getUrl());
        mergeResultData.put("userId", String.valueOf(user.getId()));
        mergeResultData.put("cosKey", finalCosKey);
        mergeResultData.put("size", String.valueOf(actualSize));
        try {
            stringRedisTemplate.opsForValue().set(finalMergeResultKey,
                    JSONUtil.toJsonStr(mergeResultData), RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        } catch (Exception e) {
            // 不抛,只 warn:幂等兜底路径仍能基于 DB 的 pictureId 重建结果
            log.warn("mergeChunks 写 Redis 幂等键失败(非阻塞): pictureId={}, err={}",
                    picture.getId(), e.getMessage());
        }

        // 9. COS 合并移到事务提交后执行（避免回滚时 COS 文件成为孤儿）
        //    合并完成后获取图片元数据（宽高），更新 picture 记录
        final Long pictureId = picture.getId();
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        boolean mergeOk = tryCompleteMultipartUpload(finalCosKey, finalUploadId, finalPartETags,
                                finalMd5, finalMergeResultKey, finalUserId);
                        // COS 合并失败时 DB 记录已提交，标 status=4（上传失败）
                        if (!mergeOk) {
                            try {
                                Picture fail = new Picture();
                                fail.setId(pictureId);
                                fail.setStatus(4); // 4=上传失败
                                pictureMapper.updateById(fail);
                                log.error("[mergeChunks] COS 合并失败,DB 记录已标失败: pictureId={}, cosKey={}", pictureId, finalCosKey);
                            } catch (Exception ex) {
                                log.error("[mergeChunks] COS 合并失败后回滚 DB status 也失败: pictureId={}", pictureId, ex);
                            }
                            // COS 合并失败,归还已扣的配额
                            try {
                                atomicUpdateSpaceSize(finalSpace, -finalSize, false);
                                log.info("[mergeChunks] COS 合并失败,已归还配额: space={}, -{} bytes", finalSpace.getId(), finalSize);
                            } catch (Exception ex) {
                                log.error("[mergeChunks] 归还配额失败: space={}, size={}", finalSpace.getId(), finalSize, ex);
                            }
                            // 清理 Redis 上传会话,避免残留 24 小时导致后续秒传误判
                            try {
                                cleanupUploadSession(finalMd5, true, finalUserId);
                            } catch (Exception ex) {
                                log.warn("[mergeChunks] COS 合并失败后清理 Redis 上传会话失败: md5={}", finalMd5, ex);
                            }
                            return;
                        }
                        // COS 合并完成后获取图片宽高等元数据
                        try {
                            PictureMessage meta = cosService.getPictureMessage(finalCosKey);
                            if (meta != null && (meta.getWidth() != null || meta.getHeight() != null)) {
                                Picture update = new Picture();
                                update.setId(pictureId);
                                update.setWidth(meta.getWidth());
                                update.setHeight(meta.getHeight());
                                pictureMapper.updateById(update);
                            }
                        } catch (Exception e) {
                            log.warn("COS 合并后获取图片元数据失败（不影响上传结果）: cosKey={}", finalCosKey, e);
                        }
                    }
                });

        PictureVO vo = PictureVO.ofUpload(picture.getId(), picture.getUrl());
        return vo;
    }

    /**
     * 根据 fileResource 创建 picture 记录（秒传场景）
     */
    private Picture createPictureFromResource(FileResource resource, Long userId, Long targetSpaceId) {
        // 获取图片元数据
        PictureMessage pictureMessage = cosService.getPictureMessage(resource.getCosKey());

        Space space = resolveTargetSpace(targetSpaceId, userId);
        checkSpaceWritePermission(space, userId);
        long size = resource.getSize();
        ExcUtils.throwIfFalse(atomicUpdateSpaceSize(space, size, true),
                ExceptionCode.PARAMETER_ERROR, "空间容量不足");

        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureMessage, picture);
        picture.setUserId(userId);
        picture.setSize(resource.getSize());
        picture.setSpaceId(space.getId());
        picture.setResourceId(resource.getId());

        // 上传到空间直接通过，无需审核
        picture.setStatus(1);

        // 秒传场景 insert 失败时也要回滚配额
        try {
            ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "保存图片失败");
        } catch (Exception e) {
            try { atomicUpdateSpaceSize(space, -size, false); } catch (Exception ex) { log.warn("空间配额回滚失败(秒传): space={}, size={}", space.getId(), size, ex); }
            // 回滚引用计数
            if (resource != null) {
                try { fileResourceService.decrementRefCount(resource.getId()); } catch (Exception ex) { log.warn("ref_count 回滚失败(秒传): resourceId={}", resource.getId(), ex); }
            }
            throw e;
        }
        return picture;
    }

    /**
     * 解析目标空间
     */
    private Space resolveTargetSpace(Long targetSpaceId, Long userId) {
        if (targetSpaceId != null) {
            Space space = spaceService.getById(targetSpaceId);
            ExcUtils.throwIfTrue(space == null, "目标空间不存在");
            validateSpaceActive(space);
            return space;
        }
        List<hk.ljx.fishpicsbackend.space.vo.SpaceVO> spaceList = spaceService.listSpace(0);
        ExcUtils.throwIfTrue(spaceList == null || spaceList.isEmpty(), "私人空间不存在，请联系管理员");
        Space space = spaceService.getById(spaceList.get(0).getId());
        ExcUtils.throwIfTrue(space == null, "私人空间不存在");
        validateSpaceActive(space);
        return space;
    }

    /**
     * 校验用户对目标空间的写权限
     * 简化版：允许空间创建者 OR 管理员 OR 团队成员
     */
    private void checkSpaceWritePermission(Space space, Long userId) {
        validateSpaceActive(space);
        // 空间创建者直接通过
        if (Objects.equals(space.getUserId(), userId)) {
            return;
        }
        // 管理员直接通过（role == 1）
        hk.ljx.fishpicsbackend.common.context.LoginContext ctx = UserHolder.getLoginContext();
        if (ctx != null && ctx.isAdmin()) {
            return;
        }
        // 检查是否是团队成员
        ExcUtils.throwIfTrue(ctx == null || ctx.getUserId() == null, ExceptionCode.NOT_LOGIN);
        // 私人空间只能创建者访问
        if (space.getType() != null && space.getType() == 0) {
            // 已认证但无权访问 → FORBIDDEN
            throw new BaseException(ExceptionCode.FORBIDDEN, "无权访问该私人空间");
        }
        Long memberCount = spaceTeamMemberMapper.selectCount(
                new LambdaQueryWrapper<hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember>()
                        .eq(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getSpaceId, space.getId())
                        .eq(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getUserId, userId)
                        .in(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getRoleId, List.of(1, 2))
        );
        // 已认证但非团队成员 → FORBIDDEN
        ExcUtils.throwIfTrue(memberCount == null || memberCount <= 0, ExceptionCode.FORBIDDEN, "无权写入该团队空间");
    }

    private void validateSpaceActive(Space space) {
        Space.validateActive(space);
    }

    /**
     * 根据用户等级获取最大上传大小
     */
    private long getMaxUploadSize(Integer level) {
        if (level == null || level <= 0) {
            return UPLOAD_MAX_SIZE_NORMAL;
        }
        return switch (level) {
            case 1 -> UPLOAD_MAX_SIZE_VIP;
            case 2 -> UPLOAD_MAX_SIZE_SVIP;
            default -> UPLOAD_MAX_SIZE_SVIP;
        };
    }

    /**
     * 原子更新空间已用大小（增量），确保不超过配额
     *
     * @param space       目标空间
     * @param size        要增加的大小（正数）或要减少的大小（负数）
     * @param isIncrement true=增加（检查配额），false=减少（保底为0）
     * @return true=更新成功，false=配额不足（仅增加时）
     */
    private boolean atomicUpdateSpaceSize(Space space, long size, boolean isIncrement) {
        Long storageSize = space.getStorageSize();
        // storageSize 为 null 或 <= 0 表示无配额限制
        if (isIncrement && storageSize != null && storageSize > 0) {
            // WHERE 守卫原子操作：只在空间充足时更新，affected==1 才表示成功
            LambdaUpdateWrapper<Space> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Space::getId, space.getId())
                    .apply("COALESCE(size, 0) + {0} <= storage_size", size)
                    .setSql("size = COALESCE(size, 0) + " + size);
            return spaceService.update(updateWrapper);
        } else if (!isIncrement) {
            LambdaUpdateWrapper<Space> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Space::getId, space.getId())
                    .setSql("size = GREATEST(COALESCE(size, 0) + " + size + ", 0)");
            return spaceService.update(updateWrapper);
        } else {
            // 增加但无配额限制 - 也用原子 SQL 避免丢失更新
            LambdaUpdateWrapper<Space> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Space::getId, space.getId())
                    .setSql("size = COALESCE(size, 0) + " + size);
            return spaceService.update(updateWrapper);
        }
    }

    /**
     * 格式化文件大小为可读字符串
     */
    private String formatSize(long bytes) {
        if (bytes >= 1073741824L) {
            return (bytes / 1073741824L) + "GB";
        } else if (bytes >= 1048576L) {
            return (bytes / 1048576L) + "MB";
        } else {
            return (bytes / 1024L) + "KB";
        }
    }

    private void bindUploadOwner(String md5, Long userId) {
        // key 加 userId 后缀,防止 MD5 公开导致跨用户会话劫持
        // 同一 MD5 不同用户各自独立会话,过期不会相互污染
        String ownerKey = RedisConstants.getUserFileUploadOwnerKey(userId, md5);
        stringRedisTemplate.opsForValue().set(ownerKey, String.valueOf(userId),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
    }

    private void validateUploadOwner(String md5, Long userId) {
        String owner = stringRedisTemplate.opsForValue()
                .get(RedisConstants.getUserFileUploadOwnerKey(userId, md5));
        ExcUtils.throwIfTrue(StrUtil.isBlank(owner),
                ExceptionCode.PARAMETER_ERROR, "上传会话已过期，请重新上传");
        ExcUtils.throwIfTrue(!owner.equals(String.valueOf(userId)),
                ExceptionCode.FORBIDDEN, "该上传会话不属于当前用户");
    }

    private void refreshUploadSessionTtl(String md5, Long userId) {
        // 用 userId-scoped key 刷新 TTL
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
        stringRedisTemplate.expire(RedisConstants.getFileMergeResultKey(md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
    }

    private long sumUploadedChunkSizes(Long userId, String md5, Integer totalChunks) {
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

    private Map<String, Object> getMergeResult(String md5) {
        String raw = stringRedisTemplate.opsForValue().get(RedisConstants.getFileMergeResultKey(md5));
        if (StrUtil.isBlank(raw)) {
            return null;
        }
        return JSONUtil.toBean(raw, Map.class);
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

    private boolean isMergedObjectAvailable(String cosKey) {
        try {
            cosService.getObjectContentType(cosKey);
            return true;
        } catch (BaseException e) {
            return false;
        }
    }

    private boolean tryCompleteMultipartUpload(String cosKey, String uploadId, List<PartETag> partETags,
                                            String md5, String mergeResultKey, Long userId) {
        try {
            cosService.completeMultipartUpload(cosKey, uploadId, partETags);
            log.info("COS multipart merge completed: cosKey={}", cosKey);
            cleanupUploadSession(md5, true, userId);
            return true;
        } catch (Exception e) {
            if (isMergedObjectAvailable(cosKey)) {
                log.warn("COS merge returned an error but object is already available: cosKey={}", cosKey, e);
                cleanupUploadSession(md5, true, userId);
                return true;
            }
            // 合并失败时放弃 COS 分片上传，避免残留 session
            cosService.abortMultipartUpload(cosKey, uploadId);
            stringRedisTemplate.expire(mergeResultKey, RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
            // 返回 false 让 caller(afterCommit 回调)把 DB picture 标 status=4
            log.error("COS multipart merge failed and session data was retained, " +
                    "需要人工/对账任务介入: cosKey={}, md5={}, error={}", cosKey, md5, e.getMessage());
            return false;
        }
    }

    private void cleanupUploadSession(String md5, boolean deleteMergeResult, Long userId) {
        // 用 userId-scoped key 清理
        Long uid = userId != null ? userId : 0L;
        stringRedisTemplate.delete(RedisConstants.getFileUploadChunksKey(uid, md5));
        stringRedisTemplate.delete(RedisConstants.getFileUploadIdKey(uid, md5));
        stringRedisTemplate.delete(RedisConstants.getFileChunkEtagKey(uid, md5));
        stringRedisTemplate.delete(RedisConstants.getFileChunkSizeKey(uid, md5));
        stringRedisTemplate.delete(RedisConstants.getFileCosKeyKey(uid, md5));
        if (userId != null) {
            // userId-scoped owner key
            stringRedisTemplate.delete(RedisConstants.getUserFileUploadOwnerKey(userId, md5));
        }
        if (deleteMergeResult) {
            stringRedisTemplate.delete(RedisConstants.getFileMergeResultKey(md5));
        }
    }

}
