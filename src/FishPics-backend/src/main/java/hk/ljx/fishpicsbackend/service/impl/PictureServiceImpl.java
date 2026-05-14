package hk.ljx.fishpicsbackend.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.CosService;
import hk.ljx.fishpicsbackend.common.utils.LoginUser;
import hk.ljx.fishpicsbackend.dto.picture.DeleteByIdList;
import hk.ljx.fishpicsbackend.dto.picture.PictureCropRequest;
import hk.ljx.fishpicsbackend.dto.picture.PictureMessage;
import hk.ljx.fishpicsbackend.dto.picture.PictureScaleRequest;
import hk.ljx.fishpicsbackend.dto.picture.PictureUpdateRequest;
import hk.ljx.fishpicsbackend.dto.picture.PictureWatermarkRequest;
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

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.AlphaComposite;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
        // 只有自己或管理员可以修改头像
        ExcUtils.throwIfTrue(!userLogin.getId().equals(id) && !userLogin.getRole().equals(ADMIN), "没有权限");
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
        String url = cosService.uploadAndGetImageUrl(file, request);
        user.setAvatar(url);
        ExcUtils.throwIfFalse(userService.updateById(user), ExceptionCode.DATABASE_ERROR, "上传失败，数据库错误");
        return url;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Picture uploadPicture(MultipartFile file, Long targetSpaceId, HttpServletRequest request) {
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
        String key = cosService.uploadPicture(file, request);
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
            List<? extends Space> spaceList = spaceService.listSpace(0, request);
            ExcUtils.throwIfTrue(spaceList == null || spaceList.isEmpty(), "私人空间不存在，请联系管理员");
            space = spaceList.get(0);
        }

        long usedSize = space.getSize() != null ? space.getSize() : 0L;
        long storageSize = space.getStorageSize() != null ? space.getStorageSize() : 0L;
        long updateSize = usedSize + size;
        if (updateSize > storageSize) {
            cosService.deletePicture(key);
            throw new BaseException(ExceptionCode.UNAUTHORIZED, "空间磁盘不足，请升级空间或删除图片");
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
        if (updateWrapper.getSqlSet() == null || updateWrapper.getSqlSet().isEmpty()) {
            return;
        }
        int rows = pictureMapper.update(null, updateWrapper);
        ExcUtils.throwIfTrue(rows == 0, "图片不存在");
    }

    private static final String FORMAT_PNG = "png";
    private static final String FORMAT_JPG = "jpg";

    /**
     * 裁剪图片
     * 处理流程：校验权限 → 从COS下载原图 → 在原图坐标空间按矩形区域裁剪 →
     * 按需旋转裁剪结果 → 编码并重新上传COS → 删除旧文件 → 更新数据库记录
     *
     * 注意：裁剪坐标使用原始图像坐标系（未经旋转），因此必须先裁剪再旋转，
     * 以保证裁剪区域与前端用户选择一致。旋转角度仅支持90的倍数（0/90/180/270）。
     *
     * @param request        裁剪请求，含图片id、裁剪区域(x/y/width/height均为原始图像像素坐标)、
     *                       旋转角度(rotation，90的倍数)、输出格式(format，默认png)
     * @param servletRequest HTTP请求
     * @return 裁剪后新图片的COS访问URL
     * @throws BaseException 图片不存在、无法读取图片、未登录时抛出
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String cropPicture(PictureCropRequest request, HttpServletRequest servletRequest) {
        Picture picture = validateAndGetPicture(request.getPictureId(), servletRequest);
        String oldUrl = picture.getUrl();
        try {
            BufferedImage sourceImage = ImgUtil.read(new URL(oldUrl));
            ExcUtils.throwIfTrue(sourceImage == null, "无法读取图片");

            int rotation = request.getRotation() != null ? request.getRotation() : 0;
            int x = (int) Math.round(request.getX() != null ? request.getX() : 0);
            int y = (int) Math.round(request.getY() != null ? request.getY() : 0);
            int width = (int) Math.round(request.getWidth() != null ? request.getWidth() : sourceImage.getWidth());
            int height = (int) Math.round(request.getHeight() != null ? request.getHeight() : sourceImage.getHeight());

            Rectangle rect = new Rectangle(x, y, Math.min(width, sourceImage.getWidth() - x),
                    Math.min(height, sourceImage.getHeight() - y));
            Image processed = ImgUtil.cut(sourceImage, rect);
            if (rotation != 0) {
                processed = ImgUtil.rotate(processed, rotation);
            }

            return applyResponsiveAndUpload(processed, request.getFormat(),
                    request.getPictureId(), oldUrl);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("裁剪图片失败", e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "裁剪图片失败：" + e.getMessage());
        }
    }

    /**
     * 缩放图片
     * 支持两种缩放方式：按比例(scale参数)或按目标宽度(targetWidth参数)等比缩放
     * 当同时传入时优先使用scale。缩放比例限制在(0, 10]区间内
     *
     * @param request        缩放请求，含图片id、缩放比例(scale)或目标宽度(targetWidth)、输出格式(format)
     * @param servletRequest HTTP请求
     * @return 缩放后新图片的COS访问URL
     * @throws BaseException 图片不存在、比例不合法、未登录时抛出
     */

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String scalePicture(PictureScaleRequest request, HttpServletRequest servletRequest) {
        Picture picture = validateAndGetPicture(request.getPictureId(), servletRequest);
        String oldUrl = picture.getUrl();
        try {
            BufferedImage sourceImage = ImgUtil.read(new URL(oldUrl));
            ExcUtils.throwIfTrue(sourceImage == null, "无法读取图片");

            double scale = request.getScale() != null ? request.getScale() : 1.0;
            if (request.getScale() == null && request.getTargetWidth() != null) {
                scale = (double) request.getTargetWidth() / sourceImage.getWidth();
            }
            ExcUtils.throwIfTrue(scale <= 0 || scale > 10, "缩放比例须在0~10之间");

            Image processed = ImgUtil.scale(sourceImage, (float) scale);
            return applyResponsiveAndUpload(processed, request.getFormat(),
                    request.getPictureId(), oldUrl);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("缩放图片失败", e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "缩放图片失败：" + e.getMessage());
        }
    }

    /**
     * 添加文字水印
     * 使用Graphics2D直接在原图上渲染半透明白色文字，利用FontMetrics实现精确居中。
     * 字号 = min(图片短边 / 30, 80px, ≥14px)，兼顾高清原图与小图的视觉协调性。
     * 透明度50%，支持中英文。中文字体按优先级尝试系统可用字体，兜底使用SANS_SERIF
     *
     * @param request        水印请求，含图片id、水印文字(text)、输出格式(format)
     * @param servletRequest HTTP请求
     * @return 添加水印后新图片的COS访问URL
     * @throws BaseException 图片不存在、水印文字为空、未登录时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String watermarkPicture(PictureWatermarkRequest request, HttpServletRequest servletRequest) {
        Picture picture = validateAndGetPicture(request.getPictureId(), servletRequest);
        String oldUrl = picture.getUrl();
        ExcUtils.throwIfTrue(request.getText() == null || request.getText().isEmpty(), "水印文字不能为空");
        try {
            BufferedImage sourceImage = ImgUtil.read(new URL(oldUrl));
            ExcUtils.throwIfTrue(sourceImage == null, "无法读取图片");

            int fontSize = Math.min(sourceImage.getWidth(), sourceImage.getHeight()) / 30;
            fontSize = Math.min(fontSize, 80);
            fontSize = Math.max(fontSize, 14);
            Font font = createChineseFont(Font.BOLD, fontSize);

            Graphics2D g2d = sourceImage.createGraphics();
            try {
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
                g2d.setColor(Color.WHITE);
                g2d.setFont(font);

                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(request.getText());
                int x = (sourceImage.getWidth() - textWidth) / 2;
                int y = (sourceImage.getHeight() - fm.getHeight()) / 2 + fm.getAscent();

                g2d.drawString(request.getText(), x, y);
            } finally {
                g2d.dispose();
            }

            return applyResponsiveAndUpload(sourceImage, request.getFormat(),
                    request.getPictureId(), oldUrl);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("添加水印失败", e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "添加水印失败：" + e.getMessage());
        }
    }

    /**
     * 校验图片存在性与用户登录状态
     * 依次校验：图片id非空 → 图片存在 → 图片URL有效 → 用户已登录
     *
     * @param pictureId      图片id
     * @param servletRequest HTTP请求，用于获取登录用户
     * @return 图片实体对象
     * @throws BaseException 图片id为空、图片不存在、图片URL为空、未登录时抛出
     */
    private Picture validateAndGetPicture(Long pictureId, HttpServletRequest servletRequest) {
        ExcUtils.throwIfTrue(ObjUtil.isEmpty(pictureId), "图片id不能为空");
        Picture picture = pictureMapper.selectById(pictureId);
        ExcUtils.throwIfTrue(picture == null, "图片不存在");
        String url = picture.getUrl();
        ExcUtils.throwIfTrue(url == null || url.isEmpty(), "图片URL为空");
        User userLogin = loginUser.getLoginUser(servletRequest);
        ExcUtils.throwIfTrue(userLogin == null || userLogin.getId() == null, "请先登录");
        return picture;
    }

    /**
     * 将处理后的图片编码、上传至COS并更新数据库
     * 处理流程：按指定格式编码图片到内存 → 上传字节流至COS → 删除COS旧文件 →
     * 更新数据库picture表(url/width/height/size) → 返回新URL
     *
     * @param image     处理后的java.awt.Image对象
     * @param format    输出格式(png/jpg)，为null时默认png
     * @param pictureId 图片id，用于更新数据库记录
     * @param oldUrl    旧图片COS URL，用于删除旧文件
     * @return 新图片的COS访问URL
     */
    private String applyResponsiveAndUpload(Image image, String format,
            Long pictureId, String oldUrl) {
        String targetFormat = format != null ? format : FORMAT_PNG;

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImgUtil.write(image, targetFormat, baos);
        byte[] imageBytes = baos.toByteArray();

        cosService.deletePictureByUrl(oldUrl);
        String suffix = "." + targetFormat;
        String newKey = cosService.uploadBytes(imageBytes, suffix);
        PictureMessage pictureMessage = cosService.getPictureMessage(newKey);
        String newUrl = pictureMessage.getUrl();

        UpdateWrapper<Picture> updateWrapper = new UpdateWrapper<Picture>().eq("id", pictureId);
        updateWrapper.set("url", newUrl);
        if (pictureMessage.getWidth() != null) {
            updateWrapper.set("width", pictureMessage.getWidth());
        }
        if (pictureMessage.getHeight() != null) {
            updateWrapper.set("height", pictureMessage.getHeight());
        }
        if (pictureMessage.getSize() != null) {
            updateWrapper.set("size", Long.parseLong(pictureMessage.getSize()));
        }
        pictureMapper.update(null, updateWrapper);
        return newUrl;
    }

    /**
     * 创建支持中文显示的Font对象
     * 按优先级依次尝试系统字体：微软雅黑 → 宋体 → SimSun → PingFang SC →
     * Noto Sans CJK SC → WenQuanYi Micro Hei，首个能渲染中文的字体即返回。
     * 若全部不可用则兜底返回SANS_SERIF（可能中文显示为方框）
     *
     * @param style 字体样式，如Font.BOLD
     * @param size  字号
     * @return 可用于渲染中文的Font对象
     */
    private Font createChineseFont(int style, int size) {
        String[] candidateFonts = { "微软雅黑", "宋体", "SimSun", "PingFang SC", "Noto Sans CJK SC", "WenQuanYi Micro Hei" };
        for (String fontName : candidateFonts) {
            Font font = new Font(fontName, style, size);
            if (font.canDisplayUpTo("中文测试") == -1) {
                return font;
            }
        }
        return new Font(Font.SANS_SERIF, style, size);
    }
}
