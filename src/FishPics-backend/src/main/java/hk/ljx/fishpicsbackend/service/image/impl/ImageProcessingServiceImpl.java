package hk.ljx.fishpicsbackend.service.image.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import hk.ljx.fishpicsbackend.common.config.ImageProcessingConfig;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.common.utils.CosService;
import hk.ljx.fishpicsbackend.common.utils.UserHolder;
import hk.ljx.fishpicsbackend.dto.picture.*;
import hk.ljx.fishpicsbackend.entity.Picture;
import hk.ljx.fishpicsbackend.entity.User;
import hk.ljx.fishpicsbackend.mapper.PictureMapper;
import hk.ljx.fishpicsbackend.service.image.ImageProcessingService;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Position;
import net.coobird.thumbnailator.geometry.Positions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class ImageProcessingServiceImpl implements ImageProcessingService {

    @Resource
    private CosService cosService;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private ImageProcessingConfig config;

    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(4);

    @Override
    public BufferedImage readImage(String url) throws IOException {
        URI uri = URI.create(url);
        return ImageIO.read(uri.toURL());
    }

    @Override
    public Picture validateAndGetPicture(Long pictureId) {
        ExcUtils.throwIfTrue(pictureId == null, "图片id不能为空");
        Picture picture = pictureMapper.selectById(pictureId);
        ExcUtils.throwIfTrue(picture == null, "图片不存在");
        String picUrl = picture.getUrl();
        ExcUtils.throwIfTrue(picUrl == null || picUrl.isEmpty(), "图片URL为空");
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null, ExceptionCode.NOT_LOGIN);
        return picture;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String compressPicture(ImageCompressRequest request) {
        Picture picture = validateAndGetPicture(request.getPictureId());
        String oldUrl = picture.getUrl();
        try {
            BufferedImage sourceImage = readImage(oldUrl);
            ExcUtils.throwIfTrue(sourceImage == null, "无法读取图片");

            double quality = request.getQuality() != null ? request.getQuality() : config.getDefaultJpegQuality();
            long targetSize = request.getTargetSizeBytes() != null ? request.getTargetSizeBytes() : config.getTargetFileSizeBytes();
            String format = request.getFormat() != null ? request.getFormat() : config.getDefaultOutputFormat();

            BufferedImage rgbImage = convertToRgbIfNeeded(sourceImage, format);

            double currentQuality = quality;
            byte[] imageBytes;
            int maxIterations = 5;

            while (true) {
                imageBytes = encodeToBytes(rgbImage, format, currentQuality);
                if (imageBytes.length <= targetSize || maxIterations <= 0) {
                    break;
                }
                currentQuality -= 0.1;
                if (currentQuality < 0.1) {
                    currentQuality = 0.1;
                }
                maxIterations--;
            }

            return uploadAndUpdate(picture, oldUrl, format, imageBytes);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("压缩图片失败", e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "压缩图片失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String generateThumbnail(ImageThumbnailRequest request) {
        Picture picture = validateAndGetPicture(request.getPictureId());
        String oldUrl = picture.getUrl();
        try {
            BufferedImage sourceImage = readImage(oldUrl);
            ExcUtils.throwIfTrue(sourceImage == null, "无法读取图片");

            int targetWidth = request.getWidth() != null ? request.getWidth() : config.getThumbnailWidth();
            int targetHeight = request.getHeight() != null ? request.getHeight() : config.getThumbnailHeight();
            String format = request.getFormat() != null ? request.getFormat() : config.getDefaultOutputFormat();

            Thumbnails.Builder<? extends BufferedImage> builder = Thumbnails.of(sourceImage)
                    .size(targetWidth, targetHeight);

            if (!request.isKeepAspectRatio()) {
                builder.forceSize(targetWidth, targetHeight);
            }

            BufferedImage thumbnail = builder.asBufferedImage();

            String targetFormat = format != null ? format : config.getDefaultOutputFormat();
            byte[] imageBytes = encodeToBytes(thumbnail, targetFormat, config.getDefaultJpegQuality());

            String suffix = "." + targetFormat;
            String newKey = cosService.uploadBytes(imageBytes, suffix);
            String newUrl = cosService.getImageUrl(newKey);
            return newUrl;
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成缩略图失败", e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "生成缩略图失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String rotatePicture(ImageRotateRequest request) {
        Picture picture = validateAndGetPicture(request.getPictureId());
        String oldUrl = picture.getUrl();
        try {
            BufferedImage sourceImage = readImage(oldUrl);
            ExcUtils.throwIfTrue(sourceImage == null, "无法读取图片");

            double angle = request.getAngle() != null ? request.getAngle() : 0;

            BufferedImage rotated = Thumbnails.of(sourceImage)
                    .scale(1.0)
                    .rotate(angle)
                    .asBufferedImage();

            String format = request.getFormat() != null ? request.getFormat() : config.getDefaultOutputFormat();
            return uploadAndUpdateToPic(picture, oldUrl, rotated, format);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("旋转图片失败", e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "旋转图片失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String autoOrientPicture(Long pictureId) {
        Picture picture = validateAndGetPicture(pictureId);
        String oldUrl = picture.getUrl();
        try {
            BufferedImage sourceImage = readImage(oldUrl);
            ExcUtils.throwIfTrue(sourceImage == null, "无法读取图片");

            int orientation = readExifOrientation(oldUrl);

            BufferedImage oriented;
            switch (orientation) {
                case 3:
                    oriented = Thumbnails.of(sourceImage).scale(1.0).rotate(180).asBufferedImage();
                    break;
                case 6:
                    oriented = Thumbnails.of(sourceImage).scale(1.0).rotate(90).asBufferedImage();
                    break;
                case 8:
                    oriented = Thumbnails.of(sourceImage).scale(1.0).rotate(270).asBufferedImage();
                    break;
                default:
                    oriented = sourceImage;
            }

            String format = config.getDefaultOutputFormat();
            return uploadAndUpdateToPic(picture, oldUrl, oriented, format);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("图片摆正失败", e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "图片摆正失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addWatermarkAdvanced(ImageWatermarkAdvancedRequest request) {
        Picture picture = validateAndGetPicture(request.getPictureId());
        String oldUrl = picture.getUrl();
        try {
            BufferedImage sourceImage = readImage(oldUrl);
            ExcUtils.throwIfTrue(sourceImage == null, "无法读取图片");

            BufferedImage rgbImage = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = rgbImage.createGraphics();
            g2d.drawImage(sourceImage, 0, 0, null);
            g2d.dispose();

            String format = request.getFormat() != null ? request.getFormat() : config.getDefaultOutputFormat();

            if (request.getImageUrl() != null && !request.getImageUrl().isEmpty()) {
                BufferedImage watermarkImage = readImage(request.getImageUrl());
                if (watermarkImage != null) {
                    float opacity = request.getOpacity() != null ? request.getOpacity() : config.getWatermarkOpacity();
                    Position position = parsePosition(request.getPosition());
                    rgbImage = Thumbnails.of(rgbImage)
                            .scale(1.0)
                            .watermark(position, watermarkImage, opacity)
                            .asBufferedImage();
                }
            }

            if (request.getText() != null && !request.getText().isEmpty()) {
                int fontSize = request.getFontSize() != null ? request.getFontSize() : config.getWatermarkFontSize();
                float opacity = request.getOpacity() != null ? request.getOpacity() : config.getWatermarkOpacity();
                Position position = parsePosition(request.getPosition());
                Font font = createChineseFont(Font.BOLD, fontSize);

                BufferedImage textWatermark = createTextWatermark(request.getText(), font, opacity);
                rgbImage = Thumbnails.of(rgbImage)
                        .scale(1.0)
                        .watermark(position, textWatermark, 1.0f)
                        .asBufferedImage();
            }

            return uploadAndUpdateToPic(picture, oldUrl, rgbImage, format);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("添加水印失败", e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "添加水印失败：" + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String convertFormat(ImageFormatConvertRequest request) {
        Picture picture = validateAndGetPicture(request.getPictureId());
        String oldUrl = picture.getUrl();
        try {
            BufferedImage sourceImage = readImage(oldUrl);
            ExcUtils.throwIfTrue(sourceImage == null, "无法读取图片");

            String targetFormat = request.getTargetFormat() != null ? request.getTargetFormat().toLowerCase() : "jpg";
            ExcUtils.throwIfTrue(!isSupportedFormat(targetFormat), "不支持的输出格式: " + targetFormat);

            double quality = request.getQuality() != null ? request.getQuality() : config.getDefaultJpegQuality();

            BufferedImage rgbImage;
            if ("jpg".equals(targetFormat) || "jpeg".equals(targetFormat)) {
                rgbImage = convertToRgbIfNeeded(sourceImage, targetFormat);
            } else {
                rgbImage = sourceImage;
            }

            byte[] imageBytes = encodeToBytes(rgbImage, targetFormat, quality);
            return uploadAndUpdate(picture, oldUrl, targetFormat, imageBytes);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("格式转换失败", e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "格式转换失败：" + e.getMessage());
        }
    }

    @Override
    public ImageBatchResult batchProcess(ImageBatchRequest request) {
        ExcUtils.throwIfTrue(request.getPictureIds() == null || request.getPictureIds().isEmpty(), "图片id列表不能为空");

        List<ImageBatchResult.ImageProcessItem> results = new ArrayList<>();
        List<CompletableFuture<ImageBatchResult.ImageProcessItem>> futures = new ArrayList<>();

        for (Long pictureId : request.getPictureIds()) {
            CompletableFuture<ImageBatchResult.ImageProcessItem> future = CompletableFuture.supplyAsync(() -> {
                try {
                    ImageCompressRequest compressRequest = new ImageCompressRequest();
                    compressRequest.setPictureId(pictureId);
                    compressRequest.setQuality(request.getQuality());
                    compressRequest.setTargetSizeBytes(request.getTargetSizeBytes());
                    compressRequest.setFormat(request.getFormat());
                    String newUrl = compressPicture(compressRequest);
                    return new ImageBatchResult.ImageProcessItem(pictureId, newUrl, true, "处理成功");
                } catch (Exception e) {
                    return new ImageBatchResult.ImageProcessItem(pictureId, null, false, e.getMessage());
                }
            }, batchExecutor);
            futures.add(future);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        int successCount = 0;
        int failCount = 0;
        for (CompletableFuture<ImageBatchResult.ImageProcessItem> future : futures) {
            ImageBatchResult.ImageProcessItem item = future.join();
            results.add(item);
            if (item.isSuccess()) {
                successCount++;
            } else {
                failCount++;
            }
        }

        return new ImageBatchResult(request.getPictureIds().size(), successCount, failCount, results);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String cleanMetadata(Long pictureId) {
        Picture picture = validateAndGetPicture(pictureId);
        String oldUrl = picture.getUrl();
        try {
            BufferedImage sourceImage = readImage(oldUrl);
            ExcUtils.throwIfTrue(sourceImage == null, "无法读取图片");

            BufferedImage cleanImage = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(),
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = cleanImage.createGraphics();
            g2d.drawImage(sourceImage, 0, 0, null);
            g2d.dispose();

            String format = config.getDefaultOutputFormat();
            return uploadAndUpdateToPic(picture, oldUrl, cleanImage, format);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("清理元数据失败", e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "清理元数据失败：" + e.getMessage());
        }
    }

    @Override
    public BufferedImage handleHeic(byte[] heicBytes) throws IOException {
        try (InputStream is = new ByteArrayInputStream(heicBytes)) {
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                throw new IOException("无法解码HEIC文件，当前JDK版本(21)不包含原生HEIC解码器。"
                        + "建议升级至JDK 22+并启用imageio-heif插件，或使用系统工具(ffmpeg/ImageMagick)预处理。");
            }
            return image;
        }
    }

    @Override
    public byte[] imageToBytes(BufferedImage image, String format) throws IOException {
        return encodeToBytes(image, format, config.getDefaultJpegQuality());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String uploadAndUpdate(Picture picture, String oldUrl, BufferedImage image, String format) {
        try {
            byte[] imageBytes = encodeToBytes(image, format, config.getDefaultJpegQuality());
            return uploadAndUpdate(picture, oldUrl, format, imageBytes);
        } catch (IOException e) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "图片编码失败：" + e.getMessage());
        }
    }

    private String uploadAndUpdateToPic(Picture picture, String oldUrl, BufferedImage image, String format) {
        try {
            byte[] imageBytes = encodeToBytes(image, format, config.getDefaultJpegQuality());
            return uploadAndUpdate(picture, oldUrl, format, imageBytes);
        } catch (IOException e) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "图片编码失败：" + e.getMessage());
        }
    }

    private String uploadAndUpdate(Picture picture, String oldUrl, String format, byte[] imageBytes) {
        cosService.deletePictureByUrl(oldUrl);
        String suffix = "." + format;
        String newKey = cosService.uploadBytes(imageBytes, suffix);
        PictureMessage pictureMessage = cosService.getPictureMessage(newKey);
        String newUrl = pictureMessage.getUrl();

        UpdateWrapper<Picture> updateWrapper = new UpdateWrapper<Picture>().eq("id", picture.getId());
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

    private Position parsePosition(String position) {
        if (position == null) return Positions.CENTER;
        switch (position.toLowerCase()) {
            case "top_left": return Positions.TOP_LEFT;
            case "top_center": return Positions.TOP_CENTER;
            case "top_right": return Positions.TOP_RIGHT;
            case "center_left": return Positions.CENTER_LEFT;
            case "center_right": return Positions.CENTER_RIGHT;
            case "bottom_left": return Positions.BOTTOM_LEFT;
            case "bottom_center": return Positions.BOTTOM_CENTER;
            case "bottom_right": return Positions.BOTTOM_RIGHT;
            default: return Positions.CENTER;
        }
    }

    private byte[] encodeToBytes(BufferedImage image, String format, double quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
            Thumbnails.of(image)
                    .scale(1.0)
                    .outputFormat("jpg")
                    .outputQuality(quality)
                    .toOutputStream(baos);
        } else if ("png".equalsIgnoreCase(format)) {
            Thumbnails.of(image)
                    .scale(1.0)
                    .outputFormat("png")
                    .toOutputStream(baos);
        } else if ("webp".equalsIgnoreCase(format)) {
            if (!ImageIO.write(image, "webp", baos)) {
                throw new IOException("WEBP编码不可用。JDK 21无内置WEBP编码器，"
                        + "请升级JDK至22+并添加NightMonkeys imageio-webp依赖，或使用JPG/PNG格式。");
            }
        } else {
            if (!ImageIO.write(image, format, baos)) {
                throw new IOException("不支持的图片格式: " + format);
            }
        }
        return baos.toByteArray();
    }

    private BufferedImage convertToRgbIfNeeded(BufferedImage image, String format) {
        if ("jpg".equalsIgnoreCase(format) || "jpeg".equalsIgnoreCase(format)) {
            if (image.getType() != BufferedImage.TYPE_INT_RGB) {
                BufferedImage rgbImage = new BufferedImage(image.getWidth(), image.getHeight(),
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = rgbImage.createGraphics();
                g2d.drawImage(image, 0, 0, null);
                g2d.dispose();
                return rgbImage;
            }
        }
        return image;
    }

    private int readExifOrientation(String imageUrl) {
        try {
            URI uri = URI.create(imageUrl);
            Metadata metadata = ImageMetadataReader.readMetadata(uri.toURL().openStream());
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            log.warn("读取EXIF方向信息失败: {}", e.getMessage());
        }
        return 1;
    }

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

    private BufferedImage createTextWatermark(String text, Font font, float opacity) {
        BufferedImage temp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2dTemp = temp.createGraphics();
        g2dTemp.setFont(font);
        FontMetrics fm = g2dTemp.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();
        g2dTemp.dispose();

        BufferedImage textImage = new BufferedImage(textWidth, textHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = textImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity));
        g2d.setFont(font);
        g2d.setColor(Color.WHITE);
        g2d.drawString(text, 0, fm.getAscent());
        g2d.dispose();

        return textImage;
    }

    private boolean isSupportedFormat(String format) {
        return "jpg".equals(format) || "jpeg".equals(format) || "png".equals(format) || "webp".equals(format);
    }
}