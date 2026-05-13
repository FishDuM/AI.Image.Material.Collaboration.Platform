package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.dto.picture.DeleteByIdList;
import hk.ljx.fishpicsbackend.dto.picture.PictureCropRequest;
import hk.ljx.fishpicsbackend.dto.picture.PictureMessage;
import hk.ljx.fishpicsbackend.dto.picture.PictureUpdateRequest;
import hk.ljx.fishpicsbackend.entity.*;
import hk.ljx.fishpicsbackend.service.*;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.vo.picture.PictureAdminVO;
import hk.ljx.fishpicsbackend.vo.picture.PictureListVO;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

import javax.imageio.ImageIO;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static hk.ljx.fishpicsbackend.common.constants.UserConstants.ADMIN;

/**
 * @author 30574
 * @description 针对表【picture(图片表)】的数据库操作Service实现
 * @createDate 2026-04-13 21:24:49
 */
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private PictureChildService pictureChildService;

    @Resource
    private CosService cosService;

    @Resource
    private PictureMapper pictureMapper;

    @Lazy
    @Resource
    private SpaceService spaceService;

    @Resource
    private LoginUser loginUser;

    @Lazy
    @Resource
    private UserService userService;

    @Lazy
    @Resource
    private PostService postService;

    @Override
    public String uploadAvatar(MultipartFile file, Long id, HttpServletRequest request) {
        User userLogin = loginUser.getLoginUser(request);
        ExcUtils.throwIfTrue(userLogin == null || userLogin.getId() == null, "请先登录");
        User user = userService.getById(userLogin.getId());
        ExcUtils.throwIfTrue(user == null, "用户不存在");
        // 只有自己或管理员可以修改头像
        ExcUtils.throwIfTrue(!userLogin.getId().equals(id) && !userLogin.getRole().equals(ADMIN), "没有权限");
        if (id != null) {
            user = userService.getById(id);
            ExcUtils.throwIfTrue(user == null, "该用户不存在");
        }
        // 删除旧头像
        if (user != null && user.getAvatar() != null) {
            cosService.deletePictureByUrl(user.getAvatar());
        }
        String url = cosService.uploadAndGetImageUrl(file);
        user.setAvatar(url);
        ExcUtils.throwIfFalse(userService.updateById(user), ExceptionCode.DATABASE_ERROR, "上传失败，数据库错误");
        return url;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Picture uploadPicture(MultipartFile file, HttpServletRequest request) {
        User userLogin = loginUser.getLoginUser(request);
        Long userId = userLogin.getId();
        Integer level = userLogin.getLevel();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(userLogin) || userId == null, "请先登录");

        // 判断用户等级 0-普通 3MB，1-VIP 5MB，2-SVIP 20MB
        if (level == 0) {
            ExcUtils.throwIfTrue(file.getSize() > 1024 * 1024 * 3, "普通用户上传图片大小不能超过3MB");
        } else if (level == 1) {
            ExcUtils.throwIfTrue(file.getSize() > 1024 * 1024 * 5, "VIP用户上传图片大小不能超过5MB");
        } else if (level == 2) {
            ExcUtils.throwIfTrue(file.getSize() > 1024 * 1024 * 20, "SVIP用户上传图片大小不能超过20MB");
        }

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
        // 判断私人空间磁盘是否充足
        long size = picture.getSize();
        List<? extends Space> spaceList = spaceService.listSpace(0, request);
        ExcUtils.throwIfTrue(spaceList == null || spaceList.isEmpty(), "私人空间不存在，请联系管理员");
        Space space = spaceList.get(0);
        long usedSize = space.getSize() != null ? space.getSize() : 0L;
        long storageSize = space.getStorageSize() != null ? space.getStorageSize() : 0L;
        long updateSize = usedSize + size;
        if (updateSize > storageSize) {
            cosService.deletePicture(key);
            throw new BaseException(ExceptionCode.UNAUTHORIZED, "私人空间磁盘不足，请升级空间或删除图片");
        }
        Boolean update = spaceService.update(space,
                new UpdateWrapper<Space>().set("size", updateSize).eq("id", space.getId()));
        ExcUtils.throwIfFalse(update, ExceptionCode.DATABASE_ERROR, "上传失败，数据库错误");
        picture.setSpaceId(space.getId());
        // 管理员上传直接通过，普通用户上传需要审核
        if (ADMIN.equals(userLogin.getRole())) {
            picture.setStatus(1);
        } else {
            picture.setStatus(2);
        }
        ExcUtils.throwIfTrue(pictureMapper.insert(picture) != 1, "上传失败，数据库错误");
        return picture;
    }

    @Override
    public IPage<PictureListVO> getPictureList(int current, int pageSize, int flag) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<Picture>()
                .eq("status", flag)
                .eq("is_private", 1)
                .isNotNull("url")
                .ne("url", "")
                .orderByDesc("create_time");
        Page<Picture> page = new Page<>(current, pageSize);
        IPage<Picture> picturePage = pictureMapper.selectPage(page, queryWrapper);
        return picturePage.convert(p -> new PictureListVO(p.getId(), p.getUrl()));
    }

    @Override
    public IPage<PictureAdminVO> getAdminPictureList(int current, int pageSize, Integer status) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<Picture>()
                .isNotNull("url")
                .ne("url", "")
                .orderByDesc("create_time");
        if (status == 0 || status == 1 || status == 2) {
            queryWrapper.eq("status", status);
        }
        if (status == 4) {
            queryWrapper.eq("is_private", 1);
        }

        Page<Picture> page = new Page<>(current, pageSize);
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
                p.getIsPrivate()));
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
    public String deletePicture(DeleteByIdList deleteByIdList, HttpServletRequest request) {
        List<Long> ids = deleteByIdList.getIds();
        ExcUtils.throwIfTrue(CollUtil.isEmpty(ids), "图片id不能为空");

        User user = loginUser.getLoginUser(request);
        String role = user.getRole();
        // 帖子封面图禁止删除
        List<Post> posts = postService.list(new QueryWrapper<Post>().in("cover", ids));
        Set<Long> count = posts.stream().map(Post::getCover).collect(Collectors.toSet());
        if (!posts.isEmpty()) {
            posts.forEach(post -> {
                ids.remove(post.getCover());
            });
        }
        if (CollUtil.isEmpty(ids)) {
            return "所选的都为帖子封面图，请先删除帖子再删图片";
        }
        // 批量查询图片
        List<Picture> pictureList = pictureMapper.selectList(new QueryWrapper<Picture>().in("id", ids));
        ExcUtils.throwIfTrue(CollUtil.isEmpty(pictureList), "图片不存在");
        Set<Long> userIds = new HashSet<>();
        pictureList.forEach(picture -> userIds.add(picture.getUserId()));
        // 判断是否为图片的主人或者管理员
        ExcUtils.throwIfFalse(role.equals(ADMIN)
                || userIds.stream().findFirst().map(id -> id.equals(user.getId())).orElse(false) || userIds.size() != 1,
                ExceptionCode.UNAUTHORIZED, "没有权限删除图片");

        int i = pictureMapper.delete(new QueryWrapper<Picture>().in("id", ids));
        ExcUtils.throwIfTrue(i == 0, "删除失败");
        pictureList.forEach(picture -> cosService.deletePictureByUrl(picture.getUrl()));
        return !count.isEmpty() ? "删除成功，但有" + count.size() + "个图片为帖子封面无法删除" : "删除成功";
    }

    @Override
    public void updatePicture(PictureUpdateRequest request) {
        Long ids = request.getIds();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(ids), "图片id不能为空");
        long count = pictureMapper.selectCount(new QueryWrapper<Picture>().eq("id", ids));
        ExcUtils.throwIfTrue(count == 0, "图片不存在");
        UpdateWrapper<Picture> updateWrapper = new UpdateWrapper<Picture>().eq("id", ids);
        if (request.getPictureName() != null) {
            updateWrapper.set("picture_name", request.getPictureName());
        }
        if (request.getIntroduction() != null) {
            updateWrapper.set("introduction", request.getIntroduction());
        }
        if (request.getPictureUrl() != null) {
            updateWrapper.set("url", request.getPictureUrl());
        }
        if (updateWrapper.getSqlSet() != null && !updateWrapper.getSqlSet().isEmpty()) {
            pictureMapper.update(null, updateWrapper);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String cropPicture(PictureCropRequest request, HttpServletRequest servletRequest) {
        Long pictureId = request.getPictureId();
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(pictureId), "图片id不能为空");

        Picture picture = pictureMapper.selectById(pictureId);
        ExcUtils.throwIfTrue(picture == null, "图片不存在");

        String oldUrl = picture.getUrl();
        ExcUtils.throwIfTrue(oldUrl == null || oldUrl.isEmpty(), "图片URL为空");

        User userLogin = loginUser.getLoginUser(servletRequest);
        ExcUtils.throwIfTrue(userLogin == null || userLogin.getId() == null, "请先登录");

        try {
            InputStream imageStream = new URL(oldUrl).openStream();
            BufferedImage sourceImage = ImageIO.read(imageStream);
            imageStream.close();
            ExcUtils.throwIfTrue(sourceImage == null, "无法读取图片");

            Integer rotation = request.getRotation() != null ? request.getRotation() : 0;
            BufferedImage rotated = sourceImage;
            if (rotation != 0) {
                double radians = Math.toRadians(rotation);
                double sin = Math.abs(Math.sin(radians));
                double cos = Math.abs(Math.cos(radians));
                int w = sourceImage.getWidth();
                int h = sourceImage.getHeight();
                int newW = (int) Math.floor(w * cos + h * sin);
                int newH = (int) Math.floor(h * cos + w * sin);
                rotated = new BufferedImage(newW, newH, sourceImage.getType());
                AffineTransform transform = new AffineTransform();
                transform.translate((newW - w) / 2.0, (newH - h) / 2.0);
                transform.rotate(radians, w / 2.0, h / 2.0);
                AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
                rotated = op.filter(sourceImage, rotated);
            }

            int x = (int) Math.round(request.getX() != null ? request.getX() : 0);
            int y = (int) Math.round(request.getY() != null ? request.getY() : 0);
            int width = (int) Math.round(request.getWidth() != null ? request.getWidth() : rotated.getWidth());
            int height = (int) Math.round(request.getHeight() != null ? request.getHeight() : rotated.getHeight());

            x = Math.max(0, Math.min(x, rotated.getWidth() - 1));
            y = Math.max(0, Math.min(y, rotated.getHeight() - 1));
            width = Math.min(width, rotated.getWidth() - x);
            height = Math.min(height, rotated.getHeight() - y);

            BufferedImage cropped = rotated.getSubimage(x, y, width, height);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(cropped, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            cosService.deletePictureByUrl(oldUrl);

            String newKey = cosService.uploadPicture(new MultipartFile() {
                @Override
                public String getName() {
                    return "file";
                }

                @Override
                public String getOriginalFilename() {
                    return "cropped.png";
                }

                @Override
                public String getContentType() {
                    return "image/png";
                }

                @Override
                public boolean isEmpty() {
                    return imageBytes.length == 0;
                }

                @Override
                public long getSize() {
                    return imageBytes.length;
                }

                @Override
                public byte[] getBytes() {
                    return imageBytes;
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(imageBytes);
                }

                @Override
                public void transferTo(java.io.File dest) throws java.io.IOException {
                    java.nio.file.Files.write(dest.toPath(), imageBytes);
                }
            });

            PictureMessage pictureMessage = cosService.getPictureMessage(newKey);
            String newUrl = pictureMessage.getUrl();

            UpdateWrapper<Picture> updateWrapper = new UpdateWrapper<Picture>().eq("id", pictureId);
            updateWrapper.set("url", newUrl);
            updateWrapper.set("width", pictureMessage.getWidth());
            updateWrapper.set("height", pictureMessage.getHeight());
            if (pictureMessage.getSize() != null) {
                updateWrapper.set("size", Long.parseLong(pictureMessage.getSize()));
            }
            pictureMapper.update(null, updateWrapper);

            return newUrl;
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("裁剪图片失败", e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "裁剪图片失败：" + e.getMessage());
        }
    }
}
