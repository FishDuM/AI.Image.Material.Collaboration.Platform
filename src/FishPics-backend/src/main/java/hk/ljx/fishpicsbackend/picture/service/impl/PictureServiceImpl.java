package hk.ljx.fishpicsbackend.picture.service.impl;
import hk.ljx.fishpicsbackend.picture.entity.Picture;
import hk.ljx.fishpicsbackend.picture.entity.FileResource;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.cache.MultiLevelCacheManager;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.CosService;
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

    private static final long MAX_CHUNK_SIZE = 5L * 1024 * 1024;

    private static final int MAX_CHUNK_COUNT = 6000;

    private void refreshUserSessionState(User user) {
        User cacheUser = new User();
        BeanUtil.copyProperties(user, cacheUser, "password", "email", "phone");
        stringRedisTemplate.opsForValue().set(
                RedisConstants.getUserInfoKey(user.getId()),
                JSONUtil.toJsonStr(cacheUser),
                RedisConstants.USER_PERM_CTX_TTL,
                TimeUnit.DAYS
        );
        cacheManager.getUserInfoCache().evict(String.valueOf(user.getId()));
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
     * 秒传命中时直接复用已有的 COS 文件，不重复上传
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
            throw new BaseException(ExceptionCode.PARAMETER_ERROR, "空间容量不足");
        }
        picture.setSpaceId(space.getId());
        // 管理员上传直接通过，普通用户上传需要审核
        hk.ljx.fishpicsbackend.common.context.LoginContext ctx = UserHolder.getLoginContext();
        if (ctx != null && ctx.hasSystemPerm("system:user:manage")) {
            picture.setStatus(1);
        } else {
            picture.setStatus(2);
        }
        try {
            ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "上传失败，数据库错误");
        } catch (Exception e) {
            // DB 失败：如果是新上传的 COS 文件，需要回滚
            if (isNewUpload && cosKey != null) {
                try { cosService.deletePicture(cosKey); } catch (Exception ex) { log.warn("COS 回滚失败: {}", cosKey, ex); }
            }
            throw e;
        }
        return picture;
    }

    /**
     * 通过 URL 保存图片
     * 把外部图片下载到临时文件 → 后续走和直接上传一样的去重/配额/审核流程
     * 用完临时文件在 finally 里删掉，不怕中途异常泄漏
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

            // 5. 空间配额检查（使用原子操作防止竞态条件）
            if (!atomicUpdateSpaceSize(space, size, true)) {
                // 仅删除新上传的 COS 文件；去重资源的 COS 文件仍被其他图片引用，不可删除
                if (isNewUpload) {
                    try { cosService.deletePicture(key); } catch (Exception ex) { log.warn("COS 回滚失败: {}", key, ex); }
                }
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "空间容量不足");
            }
            picture.setSpaceId(space.getId());

            // 管理员上传直接通过，普通用户上传需要审核
            hk.ljx.fishpicsbackend.common.context.LoginContext ctx2 = UserHolder.getLoginContext();
            if (ctx2 != null && ctx2.hasSystemPerm("system:user:manage")) {
                picture.setStatus(1);
            } else {
                picture.setStatus(2);
            }
            try {
                ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "上传失败，数据库错误");
            } catch (Exception e) {
                // DB 失败：回滚空间配额和 COS 文件
                atomicUpdateSpaceSize(space, -size, false);
                if (isNewUpload) {
                    try { cosService.deletePicture(key); } catch (Exception ex) { log.warn("COS 回滚失败: {}", key, ex); }
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

        // 当 tag 参数不为空时，模糊搜索 tags 字段；"热门"标签按默认排序（create_time DESC）
        if (StrUtil.isNotBlank(pictureQueryRequest.getTag())
                && !"热门".equals(pictureQueryRequest.getTag())) {
            queryWrapper.like(Picture::getTags, pictureQueryRequest.getTag());
        }

        Page<Picture> page = new Page<>(pictureQueryRequest.getCurrent(), pictureQueryRequest.getPageSize());
        IPage<Picture> picturePage = pictureMapper.selectPage(page, queryWrapper);
        return picturePage.convert(p -> PictureVO.ofList(p.getId(), p.getUrl(), parseTags(p.getTags())));
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
                queryWrapper.eq(Picture::getIsPrivate, 0);
            } else {
                queryWrapper.eq(Picture::getStatus, status);
            }
        }

        Page<Picture> page = new Page<>(dto.getCurrent(), dto.getPageSize());
        IPage<Picture> picturePage = pictureMapper.selectPage(page, queryWrapper);
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
                parseTags(p.getTags())));
    }

    @Override
    public void reviewPicture(Long pictureId, Integer status, Integer selected) {
        ExcUtils.throwIfTrue(pictureId == null, "图片id不能为空");
        Picture picture = pictureMapper.selectById(pictureId);
        ExcUtils.throwIfTrue(picture == null, "图片不存在");
        if (status != null) {
            ExcUtils.throwIfTrue((status != 0 && status != 1), "状态值无效");
            picture.setStatus(status);
        }
        if (selected != null) {
            ExcUtils.throwIfTrue((selected != 0 && selected != 1), "精选值无效");
            picture.setIsSelected(selected);
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
        Set<Long> userIds = new HashSet<>();
        pictureList.forEach(picture -> userIds.add(picture.getUserId()));
        // 判断权限：管理员 OR 图片所有者 OR 团队空间成员
        hk.ljx.fishpicsbackend.common.context.LoginContext ctx3 = UserHolder.getLoginContext();
        boolean isOwnerOrAdmin = (ctx3 != null && ctx3.hasSystemPerm("system:user:manage"))
                || (userIds.size() == 1 && userIds.contains(user.getId()));
        if (!isOwnerOrAdmin) {
            // 检查是否为团队空间成员（roleId 1=owner 2=member）
            Set<Long> spaceIds = new HashSet<>();
            pictureList.forEach(p -> { if (p.getSpaceId() != null) spaceIds.add(p.getSpaceId()); });
            Long memberCount = spaceTeamMemberMapper.selectCount(
                    new LambdaQueryWrapper<hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember>()
                            .eq(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getUserId, user.getId())
                            .in(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getSpaceId, spaceIds)
                            .in(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getRoleId, List.of(1, 2)));
            ExcUtils.throwIfTrue(memberCount == null || memberCount <= 0, ExceptionCode.UNAUTHORIZED, "没有权限删除图片");
        }

        int i = pictureMapper.delete(new LambdaQueryWrapper<Picture>().in(Picture::getId, ids));
        ExcUtils.throwIfTrue(i == 0, "删除失败");
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
        // 收集需要在事务提交后删除的旧数据 COS URL
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
                // 兼容旧数据（无 resourceId），记录 URL 在事务提交后删除
                legacyCosUrls.add(picture.getUrl());
            }
        }
        // 旧数据 COS 文件在事务提交后删除，避免回滚导致数据不一致
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
        hk.ljx.fishpicsbackend.common.context.LoginContext ctx4 = UserHolder.getLoginContext();
        ExcUtils.throwIfFalse(picture.getUserId().equals(user.getId()) || (ctx4 != null && ctx4.hasSystemPerm("system:user:manage")), ExceptionCode.UNAUTHORIZED, "没有权限编辑图片");

        String pictureName = request.getPictureName();
        String introduction = request.getIntroduction();
        List<String> tags = request.getTags();

        if (ObjUtil.isNotEmpty(pictureName)) {
            picture.setPictureName(pictureName);
        }
        if (ObjUtil.isNotEmpty(introduction)) {
            picture.setIntroduction(introduction);
        }
        if (ObjUtil.isNotEmpty(tags)) {
            List<String> typeList = picSystemService.getTypeList();
            Set<String> typeSet = new java.util.HashSet<>(typeList);
            boolean result = tags.stream().anyMatch(tag -> !typeSet.contains(tag));
            ExcUtils.throwIfTrue(result, ExceptionCode.PARAMETER_ERROR, "标签不存在");
            picture.setTags(JSON.toJSONString(tags));
        }

        // 协同编辑覆盖：替换图片 URL
        String newUrl = request.getUrl();
        if (StrUtil.isNotBlank(newUrl)) {
            String oldUrl = picture.getUrl();
            picture.setUrl(newUrl);
            int i = pictureMapper.updateById(picture);
            ExcUtils.throwIfFalse(i > 0, ExceptionCode.INTERNAL_SERVER_ERROR, "更新失败");
            // 异步删除旧 COS 文件（不阻塞主流程）
            if (StrUtil.isNotBlank(oldUrl)) {
                try {
                    cosService.deletePictureByUrl(oldUrl);
                    log.info("协同编辑：旧图片已删除 url={}", oldUrl);
                } catch (Exception e) {
                    log.warn("协同编辑：删除旧图片失败 url={}", oldUrl, e);
                }
            }
            return;
        }

        int i = pictureMapper.updateById(picture);
        ExcUtils.throwIfFalse(i > 0, ExceptionCode.INTERNAL_SERVER_ERROR, "更新失败");
    }

    /**
     * 协同编辑替换图片文件：上传新文件到 COS → 更新记录 → 清理旧文件
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PictureVO replacePictureFile(Long pictureId, MultipartFile file) {
        // 1. 校验
        ExcUtils.throwIfTrue(pictureId == null, "图片ID不能为空");
        ExcUtils.throwIfTrue(file == null || file.isEmpty(), "文件不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null || user.getId() == null, ExceptionCode.UNAUTHORIZED, "用户未登录");

        Picture picture = pictureMapper.selectById(pictureId);
        ExcUtils.throwIfTrue(picture == null || picture.getUserId() == null, ExceptionCode.NOT_FOUND, "图片不存在");

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
                ExceptionCode.UNAUTHORIZED, "没有权限编辑图片");

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

        // 5.1 空间配额检查（仅当新文件更大时）
        long sizeDiff = newSize - oldSize;
        if (sizeDiff > 0) {
            Space space = spaceService.getById(picture.getSpaceId());
            if (space != null) {
                Long storageSize = space.getStorageSize();
                long currentUsed = space.getSize() != null ? space.getSize() : 0;
                if (storageSize != null && storageSize > 0 && currentUsed + sizeDiff > storageSize) {
                    // 配额不足：清理新上传的 COS 文件（仅当是新上传的）
                    if (existingByMd5 == null) {
                        try { cosService.deletePicture(newCosKey); } catch (Exception ex) { log.warn("COS 回滚失败: {}", newCosKey, ex); }
                    }
                    throw new BaseException(ExceptionCode.PARAMETER_ERROR, "空间容量不足，无法保存");
                }
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
        hk.ljx.fishpicsbackend.common.context.LoginContext ctx5 = UserHolder.getLoginContext();
        ExcUtils.throwIfTrue(!picture.getUserId().equals(user.getId()) && (ctx5 == null || !ctx5.hasSystemPerm("system:user:manage")), ExceptionCode.FORBIDDEN, "无权限");

        String tags = picture.getTags();
        List<String> tagList = tags != null ? JSONUtil.toList(tags, String.class) : Collections.emptyList();

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
        // 直接返回公开图片（移除社区推荐逻辑）
        Page<Picture> page = new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize());
        Page<Picture> picturePage = baseMapper.selectPage(page, new LambdaQueryWrapper<Picture>()
                .eq(Picture::getStatus, 1)
                .eq(Picture::getIsPrivate, 0)
                .isNotNull(Picture::getUrl)
                .ne(Picture::getUrl, "")
                .orderByDesc(Picture::getCreateTime));
        return picturePage.convert(p -> PictureVO.ofList(p.getId(), p.getUrl(), parseTags(p.getTags())));
    }

    // ==================== 分片上传方法 ====================

    /**
     * 解析 tags 字段，兼容 JSON 数组格式和逗号分隔格式
     */
    private List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) return Collections.emptyList();
        String trimmed = tags.strip();
        if (trimmed.startsWith("[")) {
            try {
                return JSONUtil.toList(trimmed, String.class);
            } catch (Exception e) {
                return StrUtil.split(trimmed, ',');
            }
        }
        return StrUtil.split(trimmed, ',');
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
        String chunksKey = RedisConstants.getFileUploadChunksKey(request.getMd5());
        Long chunkCount = stringRedisTemplate.opsForSet().size(chunksKey);
        if (chunkCount != null && chunkCount > 0) {
            String uploadIdKey = RedisConstants.getFileUploadIdKey(request.getMd5());
            String uploadId = stringRedisTemplate.opsForValue().get(uploadIdKey);
            Set<String> uploadedChunks = stringRedisTemplate.opsForSet().members(chunksKey);

            // 恢复原始 cosKey，而非重新生成
            String cosKeyKey = RedisConstants.getFileCosKeyKey(request.getMd5());
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
            refreshUploadSessionTtl(request.getMd5());
            return result;
        }

        // 3. 新文件
        String cosKey = cosService.generateKey();
        // 将 cosKey 存入 Redis，供断点续传时恢复
        String cosKeyKey = RedisConstants.getFileCosKeyKey(request.getMd5());
        stringRedisTemplate.opsForValue().set(cosKeyKey, cosKey,
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        stringRedisTemplate.delete(RedisConstants.getFileMergeResultKey(request.getMd5()));
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("status", "new");
        result.put("cosKey", cosKey);
        refreshUploadSessionTtl(request.getMd5());
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
        String cosKeyKey = RedisConstants.getFileCosKeyKey(md5);
        String cosKey = stringRedisTemplate.opsForValue().get(cosKeyKey);
        ExcUtils.throwIfTrue(StrUtil.isBlank(cosKey), "上传会话已过期，请重新上传");

        // 幂等检查：如果该分片已上传，直接返回
        String chunksKey = RedisConstants.getFileUploadChunksKey(md5);
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(chunksKey, String.valueOf(chunkIndex));
        if (Boolean.TRUE.equals(isMember)) {
            String etagKey = RedisConstants.getFileChunkEtagKey(md5);
            Object existingEtagObj = stringRedisTemplate.opsForHash().get(etagKey, String.valueOf(chunkIndex));
            String existingEtag = existingEtagObj != null ? existingEtagObj.toString() : "";
            refreshUploadSessionTtl(md5);

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("etag", existingEtag);
            result.put("chunkIndex", chunkIndex);
            return result;
        }

        // 获取或初始化 uploadId（使用 setIfAbsent 防止并发请求重复初始化）
        String uploadIdKey = RedisConstants.getFileUploadIdKey(md5);
        String uploadId = stringRedisTemplate.opsForValue().get(uploadIdKey);
        if (StrUtil.isBlank(uploadId)) {
            uploadId = cosService.initiateMultipartUpload(cosKey);
            Boolean set = stringRedisTemplate.opsForValue().setIfAbsent(uploadIdKey, uploadId,
                    RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
            if (!Boolean.TRUE.equals(set)) {
                uploadId = stringRedisTemplate.opsForValue().get(uploadIdKey);
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

        String etagKey = RedisConstants.getFileChunkEtagKey(md5);
        stringRedisTemplate.opsForHash().put(etagKey, String.valueOf(chunkIndex), etag);
        stringRedisTemplate.expire(etagKey, RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        String chunkSizeKey = RedisConstants.getFileChunkSizeKey(md5);
        stringRedisTemplate.opsForHash().put(chunkSizeKey, String.valueOf(chunkIndex), String.valueOf(file.getSize()));
        stringRedisTemplate.expire(chunkSizeKey, RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        refreshUploadSessionTtl(md5);

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
        ExcUtils.throwIfTrue(StrUtil.isBlank(request.getCosKey()), "cosKey 不能为空");
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
                cleanupUploadSession(request.getMd5(), true);
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
                            JSON.toJSONString(restoredData), RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
                    PictureVO result = buildPictureVOFromMergeResult(mergeResult);
                    cleanupUploadSession(request.getMd5(), true);
                    return result;
                }
            }
        }
        validateUploadOwner(request.getMd5(), user.getId());
        ExcUtils.throwIfTrue(request.getSize() > maxSize,
                ExceptionCode.PARAMETER_ERROR, "文件大小超过限制（最大" + formatSize(maxSize) + "）");

        // 1. 完整性校验
        String chunksKey = RedisConstants.getFileUploadChunksKey(request.getMd5());
        Long uploadedCount = stringRedisTemplate.opsForSet().size(chunksKey);
        ExcUtils.throwIfTrue(uploadedCount == null || uploadedCount != request.getTotalChunks().longValue(),
                ExceptionCode.PARAMETER_ERROR, "分片不完整，已上传 " + uploadedCount + "/" + request.getTotalChunks());

        // 2. 获取 uploadId + 所有 ETag（按 chunkIndex 排序）
        String uploadIdKey = RedisConstants.getFileUploadIdKey(request.getMd5());
        String uploadId = stringRedisTemplate.opsForValue().get(uploadIdKey);
        ExcUtils.throwIfTrue(StrUtil.isBlank(uploadId), ExceptionCode.PARAMETER_ERROR, "uploadId 不存在");

        String etagKey = RedisConstants.getFileChunkEtagKey(request.getMd5());
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
        long size = sumUploadedChunkSizes(request.getMd5(), request.getTotalChunks());
        ExcUtils.throwIfTrue(size > maxSize,
                ExceptionCode.PARAMETER_ERROR, "file size exceeds limit");
        ExcUtils.throwIfFalse(mergeResult != null || atomicUpdateSpaceSize(space, size, true),
                ExceptionCode.PARAMETER_ERROR, "空间容量不足");

        // 4. COS 合并移到事务提交后执行（避免事务回滚时 COS 文件成为孤儿）
        //    使用 Redis 中存储的 cosKey，不信任客户端传入的值（防越权覆盖）
        String storedCosKey = stringRedisTemplate.opsForValue().get(RedisConstants.getFileCosKeyKey(request.getMd5()));
        ExcUtils.throwIfTrue(StrUtil.isBlank(storedCosKey), ExceptionCode.PARAMETER_ERROR, "上传会话已过期，请重新上传");
        final String finalCosKey = storedCosKey;
        final String finalUploadId = uploadId;
        final List<PartETag> finalPartETags = partETags;
        final String finalChunksKey = chunksKey;
        final String finalUploadIdKey = uploadIdKey;
        final String finalEtagKey = etagKey;
        final String finalMd5 = request.getMd5();
        final String finalMergeResultKey = RedisConstants.getFileMergeResultKey(request.getMd5());

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

        // 8. 存储合并结果（用于幂等重试：下次 mergeChunks 可直接返回已创建的 picture）
        Map<String, String> mergeResultData = new HashMap<>();
        mergeResultData.put("pictureId", String.valueOf(picture.getId()));
        mergeResultData.put("url", picture.getUrl());
        mergeResultData.put("userId", String.valueOf(user.getId()));
        mergeResultData.put("cosKey", finalCosKey);
        mergeResultData.put("size", String.valueOf(actualSize));
        stringRedisTemplate.opsForValue().set(finalMergeResultKey,
                JSON.toJSONString(mergeResultData), RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);

        // 9. COS 合并移到事务提交后执行（避免回滚时 COS 文件成为孤儿）
        //    合并完成后获取图片元数据（宽高），更新 picture 记录
        final Long pictureId = picture.getId();
        org.springframework.transaction.support.TransactionSynchronizationManager
                .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        tryCompleteMultipartUpload(finalCosKey, finalUploadId, finalPartETags,
                                finalMd5, finalMergeResultKey);
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

        hk.ljx.fishpicsbackend.common.context.LoginContext ctx = UserHolder.getLoginContext();
        if (ctx != null && ctx.hasSystemPerm("system:user:manage")) {
            picture.setStatus(1);
        } else {
            picture.setStatus(2);
        }
        ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "保存图片失败");
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
        // 管理员直接通过（level >= 3）
        User user = UserHolder.getUser();
        if (user != null && user.getLevel() != null && user.getLevel() >= 3) {
            return;
        }
        // 检查是否是团队成员
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        // 私人空间只能创建者访问
        if (space.getType() != null && space.getType() == 0) {
            throw new BaseException(ExceptionCode.UNAUTHORIZED, "无权访问该私人空间");
        }
        Long memberCount = spaceTeamMemberMapper.selectCount(
                new LambdaQueryWrapper<hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember>()
                        .eq(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getSpaceId, space.getId())
                        .eq(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getUserId, userId)
                        .in(hk.ljx.fishpicsbackend.space.entity.SpaceTeamMember::getRoleId, List.of(1, 2))
        );
        ExcUtils.throwIfTrue(memberCount == null || memberCount <= 0, ExceptionCode.UNAUTHORIZED, "无权写入该团队空间");
    }

    private void validateSpaceActive(Space space) {
        ExcUtils.throwIfTrue(space == null, ExceptionCode.PARAMETER_ERROR, "空间不存在");
        ExcUtils.throwIfTrue(!Integer.valueOf(1).equals(space.getStatus()), ExceptionCode.FORBIDDEN, "空间已被禁用");
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
        String ownerKey = RedisConstants.getFileUploadOwnerKey(md5);
        String existingOwner = stringRedisTemplate.opsForValue().get(ownerKey);
        ExcUtils.throwIfTrue(StrUtil.isNotBlank(existingOwner)
                        && !existingOwner.equals(String.valueOf(userId)),
                ExceptionCode.FORBIDDEN, "该上传会话不属于当前用户");
        stringRedisTemplate.opsForValue().set(ownerKey, String.valueOf(userId),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
    }

    private void validateUploadOwner(String md5, Long userId) {
        String owner = stringRedisTemplate.opsForValue().get(RedisConstants.getFileUploadOwnerKey(md5));
        ExcUtils.throwIfTrue(StrUtil.isBlank(owner),
                ExceptionCode.PARAMETER_ERROR, "上传会话已过期，请重新上传");
        ExcUtils.throwIfTrue(!owner.equals(String.valueOf(userId)),
                ExceptionCode.FORBIDDEN, "该上传会话不属于当前用户");
    }

    private void refreshUploadSessionTtl(String md5) {
        stringRedisTemplate.expire(RedisConstants.getFileUploadOwnerKey(md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        stringRedisTemplate.expire(RedisConstants.getFileCosKeyKey(md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        stringRedisTemplate.expire(RedisConstants.getFileUploadIdKey(md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        stringRedisTemplate.expire(RedisConstants.getFileUploadChunksKey(md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        stringRedisTemplate.expire(RedisConstants.getFileChunkEtagKey(md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        stringRedisTemplate.expire(RedisConstants.getFileChunkSizeKey(md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        stringRedisTemplate.expire(RedisConstants.getFileMergeResultKey(md5),
                RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
    }

    private long sumUploadedChunkSizes(String md5, Integer totalChunks) {
        Map<Object, Object> chunkSizeMap = stringRedisTemplate.opsForHash()
                .entries(RedisConstants.getFileChunkSizeKey(md5));
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
        return JSON.parseObject(raw, Map.class);
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

    private void tryCompleteMultipartUpload(String cosKey, String uploadId, List<PartETag> partETags,
                                            String md5, String mergeResultKey) {
        try {
            cosService.completeMultipartUpload(cosKey, uploadId, partETags);
            log.info("COS multipart merge completed: cosKey={}", cosKey);
            cleanupUploadSession(md5, true);
        } catch (Exception e) {
            if (isMergedObjectAvailable(cosKey)) {
                log.warn("COS merge returned an error but object is already available: cosKey={}", cosKey, e);
                cleanupUploadSession(md5, true);
                return;
            }
            stringRedisTemplate.expire(mergeResultKey, RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
            log.error("COS multipart merge failed and session data was retained: cosKey={}", cosKey, e);
        }
    }

    private void cleanupUploadSession(String md5, boolean deleteMergeResult) {
        stringRedisTemplate.delete(RedisConstants.getFileUploadChunksKey(md5));
        stringRedisTemplate.delete(RedisConstants.getFileUploadIdKey(md5));
        stringRedisTemplate.delete(RedisConstants.getFileChunkEtagKey(md5));
        stringRedisTemplate.delete(RedisConstants.getFileChunkSizeKey(md5));
        stringRedisTemplate.delete(RedisConstants.getFileCosKeyKey(md5));
        stringRedisTemplate.delete(RedisConstants.getFileUploadOwnerKey(md5));
        if (deleteMergeResult) {
            stringRedisTemplate.delete(RedisConstants.getFileMergeResultKey(md5));
        }
    }

}
