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
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.CosService;
import hk.ljx.fishpicsbackend.common.utils.DownloadUtils;
import hk.ljx.fishpicsbackend.common.utils.FileTypeUtils;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
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
import hk.ljx.fishpicsbackend.picture.vo.PictureAdminVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureEditVO;
import hk.ljx.fishpicsbackend.picture.vo.PictureListVO;
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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qcloud.cos.model.PartETag;
import hk.ljx.fishpicsbackend.common.constants.RedisConstants;
import hk.ljx.fishpicsbackend.common.dto.PageRequest;
import hk.ljx.fishpicsbackend.permission.service.PermissionService;
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
    private PermissionService permissionService;

    @Resource
    private FileResourceService fileResourceService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public String uploadAvatar(MultipartFile file, Long id) {
        User userLogin = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(userLogin), ExceptionCode.NOT_LOGIN);
        // 只有自己或管理员可以修改头像
        hk.ljx.fishpicsbackend.common.context.LoginContext ctx = UserHolder.getLoginContext();
        ExcUtils.throwIfTrue(!userLogin.getId().equals(id) && (ctx == null || !ctx.hasSystemPerm("system:user:manage")), "没有权限");
        User user;
        if (id != null && !id.equals(userLogin.getId())) {
            user = userService.getById(id);
            ExcUtils.throwIfTrue(user == null, "该用户不存在");
        } else {
            user = userService.getById(userLogin.getId());
            ExcUtils.throwIfTrue(user == null, "用户不存在");
        }
        // 删除旧头像
        if (user.getAvatar() != null) {
            cosService.deletePictureByUrl(user.getAvatar());
        }
        String url = cosService.uploadAndGetImageUrl(file);
        user.setAvatar(url);
        ExcUtils.throwIfFalse(userService.updateById(user), ExceptionCode.DATABASE_ERROR, "上传失败，数据库错误");
        return url;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Picture uploadPicture(MultipartFile file, Long targetSpaceId) {
        User userLogin = UserHolder.getUser();
        Long userId = userLogin.getId();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(userLogin) || userId == null, "请先登录");

        // 按用户等级动态限制上传大小
        long maxSize = getMaxUploadSize(userLogin.getLevel());
        ExcUtils.throwIfTrue(file.getSize() > maxSize,
                "上传图片大小不能超过" + formatSize(maxSize));

        // 文件类型校验（魔数检测）
        String validFileType = FileTypeUtils.getValidFileType(file);
        ExcUtils.throwIfTrue(validFileType == null, "上传文件格式不正确");

        // 上传图片
        String key = cosService.uploadPicture(file);
        // 获取图片信息
        PictureMessage pictureMessage = cosService.getPictureMessage(key);
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureMessage, picture);
        picture.setUserId(userId);
        // BeanUtil 可能无法将 String size 转为 Long，手动转换兜底
        if (picture.getSize() == null && pictureMessage.getSize() != null) {
            picture.setSize(Long.parseLong(pictureMessage.getSize()));
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

        Long storageSize = space.getStorageSize();
        if (storageSize != null) {
            // 使用原子 SQL 增量更新空间大小，确保不超过配额（防止竞态条件）
            // COALESCE处理size为NULL的情况
            UpdateWrapper<Space> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", space.getId())
                    .setSql("size = COALESCE(size, 0) + " + size)
                    .le("COALESCE(size, 0) + " + size, storageSize);  // 确保更新后不超过配额
            Boolean update = spaceService.update(updateWrapper);
            ExcUtils.throwIfFalse(update, ExceptionCode.PARAMETER_ERROR, "空间容量不足");
        } else {
            // 如果没有设置配额限制，直接更新
            long usedSize = space.getSize() != null ? space.getSize() : 0L;
            long updateSize = usedSize + size;
            space.setSize(updateSize);
            spaceService.updateById(space);
        }
        picture.setSpaceId(space.getId());
        // 管理员上传直接通过，普通用户上传需要审核
        hk.ljx.fishpicsbackend.common.context.LoginContext ctx = UserHolder.getLoginContext();
        if (ctx != null && ctx.hasSystemPerm("system:user:manage")) {
            picture.setStatus(1);
        } else {
            picture.setStatus(2);
        }
        ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "上传失败，数据库错误");
        return picture;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Picture savePictureByUrl(String url, Long targetSpaceId) {
        User userLogin = UserHolder.getUser();
        Long userId = userLogin.getId();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(userLogin) || userId == null, "请先登录");
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

            // 3. 上传到 COS
            String key;
            try (FileInputStream fis = new FileInputStream(tempFile)) {
                key = cosService.uploadPicture(fis, tempFile.length());
            } catch (IOException e) {
                throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "读取临时文件失败");
            }

            // 4. 获取 COS 图片信息
            PictureMessage pictureMessage = cosService.getPictureMessage(key);
            Picture picture = new Picture();
            BeanUtil.copyProperties(pictureMessage, picture);
            picture.setUserId(userId);
            if (picture.getSize() == null && pictureMessage.getSize() != null) {
                picture.setSize(Long.parseLong(pictureMessage.getSize()));
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

            // 5. 空间配额检查（使用原子操作防止竞态条件）
            Long storageSize = space.getStorageSize();
            if (storageSize != null) {
                // 使用原子 SQL 增量更新空间大小，确保不超过配额
                // COALESCE处理size为NULL的情况
                UpdateWrapper<Space> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("id", space.getId())
                        .setSql("size = COALESCE(size, 0) + " + size)
                        .le("COALESCE(size, 0) + " + size, storageSize);  // 确保更新后不超过配额
                Boolean update = spaceService.update(updateWrapper);
                if (!update) {
                    cosService.deletePicture(key);
                    throw new BaseException(ExceptionCode.PARAMETER_ERROR, "空间容量不足");
                }
            } else {
                // 如果没有设置配额限制，直接更新
                long usedSize = space.getSize() != null ? space.getSize() : 0L;
                long updateSize = usedSize + size;
                space.setSize(updateSize);
                spaceService.updateById(space);
            }
            picture.setSpaceId(space.getId());

            // 管理员上传直接通过，普通用户上传需要审核
            hk.ljx.fishpicsbackend.common.context.LoginContext ctx2 = UserHolder.getLoginContext();
            if (ctx2 != null && ctx2.hasSystemPerm("system:user:manage")) {
                picture.setStatus(1);
            } else {
                picture.setStatus(2);
            }
            ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "上传失败，数据库错误");
            return picture;

        } finally {
            // 6. 清理临时文件
            FileUtil.del(tempFile);
        }
    }

    @Override
    public IPage<PictureListVO> getPictureList(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<Picture>()
                .eq("status", 1)
                .eq("is_private", 1)
                .isNotNull("url")
                .ne("url", "")
                .orderByDesc("create_time");

        // 当 tag 参数不为空时，模糊搜索 tags 字段；"热门"标签按默认排序（create_time DESC）
        if (StrUtil.isNotBlank(pictureQueryRequest.getTag())
                && !"热门".equals(pictureQueryRequest.getTag())) {
            queryWrapper.like("tags", pictureQueryRequest.getTag());
        }

        Page<Picture> page = new Page<>(pictureQueryRequest.getCurrent(), pictureQueryRequest.getPageSize());
        IPage<Picture> picturePage = pictureMapper.selectPage(page, queryWrapper);
        return picturePage.convert(p -> new PictureListVO(p.getId(), p.getUrl(), StrUtil.split(p.getTags(), ",")));
    }

    @Override
    public IPage<PictureAdminVO> getAdminPictureList(AdminPictureListDTO dto) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<Picture>()
                .isNotNull("url")
                .ne("url", "")
                .orderByDesc("create_time");

        Integer status = dto.getStatus();
        if (status != null) {
            if (status == 4) {
                queryWrapper.eq("is_private", 1);
            } else {
                queryWrapper.eq("status", status);
            }
        }

        Page<Picture> page = new Page<>(dto.getCurrent(), dto.getPageSize());
        IPage<Picture> picturePage = pictureMapper.selectPage(page, queryWrapper);
        return picturePage.convert(p -> new PictureAdminVO(
                p.getId(),
                p.getUrl(),
                p.getWidth(),
                p.getHeight(),
                p.getSize(),
                p.getStatus(),
                p.getCreateTime(),
                p.getUserId(),
                p.getIsPrivate(),
                StrUtil.split(p.getTags(), ",")));
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
            picture.setIsPrivate(selected);
        }
        ExcUtils.throwIfTrue(pictureMapper.updateById(picture) != 1, "审核更新失败");
    }

    @Override
    public String deletePicture(DeleteByIdList deleteByIdList) {
        List<Long> ids = deleteByIdList.getIds();
        ExcUtils.throwIfTrue(CollUtil.isEmpty(ids), "图片id不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);

        // 批量查询图片
        List<Picture> pictureList = pictureMapper.selectList(new QueryWrapper<Picture>().in("id", ids));
        ExcUtils.throwIfTrue(CollUtil.isEmpty(pictureList), "图片不存在");
        Set<Long> userIds = new HashSet<>();
        pictureList.forEach(picture -> userIds.add(picture.getUserId()));
        // 判断是否为图片的主人或者管理员
        hk.ljx.fishpicsbackend.common.context.LoginContext ctx3 = UserHolder.getLoginContext();
        ExcUtils.throwIfFalse((ctx3 != null && ctx3.hasSystemPerm("system:user:manage"))
                || (userIds.size() == 1 && userIds.contains(user.getId())),
                ExceptionCode.UNAUTHORIZED, "没有权限删除图片");

        int i = pictureMapper.delete(new QueryWrapper<Picture>().in("id", ids));
        ExcUtils.throwIfTrue(i == 0, "删除失败");
        // 扣减空间已用大小
        Map<Long, Long> spaceSizeMap = new java.util.HashMap<>();
        pictureList.forEach(picture -> {
            if (picture.getSpaceId() != null && picture.getSize() != null) {
                spaceSizeMap.merge(picture.getSpaceId(), picture.getSize(), Long::sum);
            }
        });
        spaceSizeMap.forEach((spaceId, deletedSize) -> {
            Space space = spaceService.getById(spaceId);
            if (space != null) {
                long newSize = Math.max(0, space.getSize() - deletedSize);
                Space update = new Space();
                update.setId(spaceId);
                update.setSize(newSize);
                spaceService.updateById(update);
            }
        });
        // 同步删除 COS 对象
        pictureList.forEach(picture -> cosService.deletePictureByUrl(picture.getUrl()));
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
            boolean result = tags.stream().anyMatch(tag -> !typeList.contains(tag));
            ExcUtils.throwIfTrue(result, ExceptionCode.PARAMETER_ERROR, "标签不存在");
            picture.setTags(JSON.toJSONString(tags));
        }

        int i = pictureMapper.updateById(picture);
        ExcUtils.throwIfFalse(i > 0, ExceptionCode.INTERNAL_SERVER_ERROR, "更新失败");
    }

    /**
     * 编辑时图片信息回填
     *
     * @param id 图片id
     * @return 图片信息
     */
    @Override
    public PictureEditVO getPictureEditMessage(Long id) {
        ExcUtils.throwIfTrue(id == null, "图片id不能为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null || user.getId() == null, ExceptionCode.NOT_LOGIN);
        Picture picture = pictureMapper.selectById(id);
        ExcUtils.throwIfTrue(picture == null || picture.getUserId() == null, ExceptionCode.NOT_FOUND, "图片不存在");
        hk.ljx.fishpicsbackend.common.context.LoginContext ctx5 = UserHolder.getLoginContext();
        ExcUtils.throwIfTrue(!picture.getUserId().equals(user.getId()) && (ctx5 == null || !ctx5.hasSystemPerm("system:user:manage")), ExceptionCode.FORBIDDEN, "无权限");
        PictureEditVO pictureEditVO = new PictureEditVO();
        pictureEditVO.setPictureName(picture.getPictureName());
        pictureEditVO.setIntroduction(picture.getIntroduction());
        String tags = picture.getTags();
        List<String> tagList = JSONUtil.toList(tags, String.class);
        pictureEditVO.setTags(tagList);
        return pictureEditVO;
    }

    @Override
    public IPage<PictureListVO> getRecommendPictures(PageRequest pageRequest, Long userId) {
        // 直接返回公开图片（移除社区推荐逻辑）
        Page<Picture> page = new Page<>(pageRequest.getCurrent(), pageRequest.getPageSize());
        Page<Picture> picturePage = baseMapper.selectPage(page, new LambdaQueryWrapper<Picture>()
                .eq(Picture::getStatus, 1)
                .eq(Picture::getIsPrivate, 1)
                .isNotNull(Picture::getUrl)
                .ne(Picture::getUrl, "")
                .orderByDesc(Picture::getCreateTime));
        return picturePage.convert(p -> new PictureListVO(p.getId(), p.getUrl(),
                p.getTags() == null ? Collections.emptyList() : StrUtil.split(p.getTags(), ",")));
    }

    // ==================== 分片上传方法 ====================

    @Override
    public Object checkUpload(CheckUploadRequest request) {
        ExcUtils.throwIfTrue(StrUtil.isBlank(request.getMd5()), "MD5 不能为空");
        ExcUtils.throwIfTrue(request.getSize() == null, "文件大小不能为空");

        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(user) || user.getId() == null, ExceptionCode.NOT_LOGIN);

        // 按用户等级限制文件大小
        long maxSize = getMaxUploadSize(user.getLevel());
        ExcUtils.throwIfTrue(request.getSize() > maxSize,
                "文件大小超过限制（最大" + formatSize(maxSize) + "）");

        // 1. 查 file_resource 表（秒传）
        FileResource resource = fileResourceService.findByMd5AndSize(request.getMd5(), request.getSize());
        if (resource != null) {
            // 秒传命中：增加引用计数，为当前用户创建 picture 记录
            fileResourceService.incrementRefCount(resource.getId());

            Picture picture = createPictureFromResource(resource, user.getId(), request.getTargetSpaceId());
            PictureListVO vo = new PictureListVO();
            vo.setId(picture.getId());
            vo.setUrl(picture.getUrl());

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

            String cosKey = cosService.generateKey();

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("status", "resume");
            result.put("uploadedChunks", uploadedChunks != null ?
                    uploadedChunks.stream().map(Integer::parseInt).sorted().toList() : List.of());
            result.put("uploadId", uploadId);
            result.put("cosKey", cosKey);
            return result;
        }

        // 3. 新文件
        String cosKey = cosService.generateKey();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("status", "new");
        result.put("cosKey", cosKey);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Object uploadChunk(MultipartFile file, String md5, Integer chunkIndex, String cosKey) {
        ExcUtils.throwIfTrue(file == null || file.isEmpty(), "分片文件不能为空");
        ExcUtils.throwIfTrue(StrUtil.isBlank(md5), "MD5 不能为空");
        ExcUtils.throwIfTrue(chunkIndex == null, "分片编号不能为空");
        ExcUtils.throwIfTrue(StrUtil.isBlank(cosKey), "cosKey 不能为空");

        // 幂等检查：如果该分片已上传，直接返回
        String chunksKey = RedisConstants.getFileUploadChunksKey(md5);
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(chunksKey, String.valueOf(chunkIndex));
        if (Boolean.TRUE.equals(isMember)) {
            String etagKey = RedisConstants.getFileChunkEtagKey(md5);
            String existingEtag = stringRedisTemplate.opsForHash().get(etagKey, String.valueOf(chunkIndex)).toString();

            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("etag", existingEtag);
            result.put("chunkIndex", chunkIndex);
            return result;
        }

        // 获取或初始化 uploadId
        String uploadIdKey = RedisConstants.getFileUploadIdKey(md5);
        String uploadId = stringRedisTemplate.opsForValue().get(uploadIdKey);
        if (StrUtil.isBlank(uploadId)) {
            uploadId = cosService.initiateMultipartUpload(cosKey);
            stringRedisTemplate.opsForValue().set(uploadIdKey, uploadId,
                    RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);
        }

        // 上传分片到 COS（分片编号从 1 开始）
        String etag;
        try (InputStream is = file.getInputStream()) {
            etag = cosService.uploadPart(cosKey, uploadId, chunkIndex + 1, is, file.getSize());
        } catch (IOException e) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "分片上传失败");
        }

        // 记录到 Redis
        stringRedisTemplate.opsForSet().add(chunksKey, String.valueOf(chunkIndex));
        stringRedisTemplate.expire(chunksKey, RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);

        String etagKey = RedisConstants.getFileChunkEtagKey(md5);
        stringRedisTemplate.opsForHash().put(etagKey, String.valueOf(chunkIndex), etag);
        stringRedisTemplate.expire(etagKey, RedisConstants.FILE_UPLOAD_TTL, TimeUnit.HOURS);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("etag", etag);
        result.put("chunkIndex", chunkIndex);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PictureListVO mergeChunks(MergeChunksRequest request) {
        ExcUtils.throwIfTrue(StrUtil.isBlank(request.getMd5()), "MD5 不能为空");
        ExcUtils.throwIfTrue(request.getSize() == null, "文件大小不能为空");
        ExcUtils.throwIfTrue(StrUtil.isBlank(request.getCosKey()), "cosKey 不能为空");
        ExcUtils.throwIfTrue(request.getTotalChunks() == null || request.getTotalChunks() <= 0, "分片总数无效");

        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(user) || user.getId() == null, ExceptionCode.NOT_LOGIN);

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

        List<PartETag> partETags = etagMap.entrySet().stream()
                .sorted((a, b) -> Integer.compare(
                        Integer.parseInt(a.getKey().toString()),
                        Integer.parseInt(b.getKey().toString())))
                .map(entry -> new PartETag(
                        Integer.parseInt(entry.getKey().toString()) + 1,
                        entry.getValue().toString()))
                .toList();

        // 3. COS 合并
        cosService.completeMultipartUpload(request.getCosKey(), uploadId, partETags);

        // 4. 获取图片元数据
        PictureMessage pictureMessage = cosService.getPictureMessage(request.getCosKey());

        // 5. 计算 MD5 并创建 file_resource 记录
        FileResource resource = fileResourceService.addResource(
                request.getMd5(), request.getSize(), request.getCosKey());

        // 6. 确定目标空间
        Space space = resolveTargetSpace(request.getTargetSpaceId(), user.getId());
        long size = request.getSize();
        Long storageSize = space.getStorageSize();
        if (storageSize != null) {
            UpdateWrapper<Space> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", space.getId())
                    .setSql("size = COALESCE(size, 0) + " + size)
                    .le("COALESCE(size, 0) + " + size, storageSize);
            Boolean updated = spaceService.update(updateWrapper);
            if (!updated) {
                cosService.deletePicture(request.getCosKey());
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "空间容量不足");
            }
        }

        // 7. 创建 picture 记录
        Picture picture = new Picture();
        BeanUtil.copyProperties(pictureMessage, picture);
        picture.setUserId(user.getId());
        picture.setSize(request.getSize());
        picture.setSpaceId(space.getId());
        picture.setResourceId(resource.getId());

        hk.ljx.fishpicsbackend.common.context.LoginContext ctx = UserHolder.getLoginContext();
        if (ctx != null && ctx.hasSystemPerm("system:user:manage")) {
            picture.setStatus(1);
        } else {
            picture.setStatus(2);
        }
        ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "保存图片失败");

        // 8. 清理 Redis
        stringRedisTemplate.delete(chunksKey);
        stringRedisTemplate.delete(uploadIdKey);
        stringRedisTemplate.delete(etagKey);

        PictureListVO vo = new PictureListVO();
        vo.setId(picture.getId());
        vo.setUrl(picture.getUrl());
        return vo;
    }

    /**
     * 根据 fileResource 创建 picture 记录（秒传场景）
     */
    private Picture createPictureFromResource(FileResource resource, Long userId, Long targetSpaceId) {
        // 获取图片元数据
        PictureMessage pictureMessage = cosService.getPictureMessage(resource.getCosKey());

        Space space = resolveTargetSpace(targetSpaceId, userId);
        long size = resource.getSize();
        Long storageSize = space.getStorageSize();
        if (storageSize != null) {
            UpdateWrapper<Space> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", space.getId())
                    .setSql("size = COALESCE(size, 0) + " + size)
                    .le("COALESCE(size, 0) + " + size, storageSize);
            Boolean updated = spaceService.update(updateWrapper);
            if (!updated) {
                throw new BaseException(ExceptionCode.PARAMETER_ERROR, "空间容量不足");
            }
        }

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
            return space;
        }
        List<hk.ljx.fishpicsbackend.space.vo.SpaceVO> spaceList = spaceService.listSpace(0);
        ExcUtils.throwIfTrue(spaceList == null || spaceList.isEmpty(), "私人空间不存在，请联系管理员");
        Space space = spaceService.getById(spaceList.get(0).getId());
        ExcUtils.throwIfTrue(space == null, "私人空间不存在");
        return space;
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
            default -> UPLOAD_MAX_SIZE_NORMAL;
        };
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

}
