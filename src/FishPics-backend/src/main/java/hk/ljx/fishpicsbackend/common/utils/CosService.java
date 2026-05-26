package hk.ljx.fishpicsbackend.common.utils;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.picture.dto.PictureMessage;
import hk.ljx.fishpicsbackend.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CosService {

    @Resource
    private COSClient cosClient;

    @Value("${cos.region}")
    private String region;

    @Value("${cos.bucket}")
    private String bucket;

    @Value("${cos.public:false}")
    private boolean isPublic = true;

    @Value("${cos.url}")
    private String url;

    // ====================== 配置常量 ======================
    /**
     * 普通最大文件大小 5MB
     */
    private static final long PT_MAX_SIZE = 5 * 1024 * 1024L;

    /**
     * VIP 最大单文件大小 10MB
     */
    private static final long VIP_MAX_SIZE = 10 * 1024 * 1024L;

    /**
     * SVIP 最大单文件大小 50MB
     */
    private static final long SVIP_MAX_SIZE = 50 * 1024 * 1024L;

    /**
     * 允许上传的图片类型
     */
    private static final List<String> ALLOWED_IMG_TYPES = Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/jpg",
            "image/gif",
            "image/webp",
            "image/heic",
            "image/heif");

    /**
     * 文件扩展名 → MIME类型映射，用于浏览器未正确上报contentType时的兜底校验
     */
    private static final Map<String, String> EXT_TO_MIME = new java.util.HashMap<>();
    static {
        EXT_TO_MIME.put(".jpg", "image/jpeg");
        EXT_TO_MIME.put(".jpeg", "image/jpeg");
        EXT_TO_MIME.put(".png", "image/png");
        EXT_TO_MIME.put(".gif", "image/gif");
        EXT_TO_MIME.put(".webp", "image/webp");
        EXT_TO_MIME.put(".heic", "image/heic");
        EXT_TO_MIME.put(".heif", "image/heif");
    }

    // 临时URL有效期：10分钟（生产环境私有读写用）
    private static final long URL_EXPIRE_SECONDS = 10 * 60L;

    /**
     * 上传路径前缀
     */
    private static final String UPLOAD_PREFIX = "picture/";

    // ====================== 核心上传方法 ======================
    /**
     * 上传图片到腾讯云COS（流式上传，不落地本地磁盘）
     *
     * @param file 前端上传的文件
     * @return cos文件唯一key
     */
    public String uploadPicture(MultipartFile file) {
        // 1. 校验文件不能为空
        ExcUtils.throwIfTrue(file.isEmpty(), "上传文件不能为空");

        // 2. 校验是否为图片类型（优先用浏览器上报的MIME，不可用时从扩展名反推）
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMG_TYPES.contains(contentType)) {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null && originalFilename.contains(".")) {
                String ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
                contentType = EXT_TO_MIME.get(ext);
            }
        }
        ExcUtils.throwIfTrue(contentType == null || !ALLOWED_IMG_TYPES.contains(contentType),
                "只能上传 JPG、PNG、GIF、WEBP、HEIC 格式的图片");

        // 3. 校验文件名合法性
        String originalFilename = file.getOriginalFilename();
        ExcUtils.throwIfTrue(originalFilename == null || !originalFilename.contains("."),
                "文件名不合法，请上传带后缀的图片");

        // 4. 生成唯一文件路径（防止重复 + 安全）
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        String uuidFileName = UUID.randomUUID().toString(true) + suffix;
        String key = UPLOAD_PREFIX + System.currentTimeMillis() + "_" + uuidFileName;

        // 获取用户的等级对应上传大小
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(ObjectUtil.isEmpty(user), ExceptionCode.NOT_LOGIN);
        Integer level = user.getLevel();
        long size;
        switch (level) {
            case 1:
                size = VIP_MAX_SIZE;
                break;
            case 2:
                size = SVIP_MAX_SIZE;
                break;
            default:
                size = PT_MAX_SIZE;
        }

        // 5. 流式上传COS（自动大小限制，不落地）
        try (InputStream inputStream = file.getInputStream();
                LimitedInputStream limitedInputStream = new LimitedInputStream(inputStream, size)) {

            PutObjectRequest req = new PutObjectRequest(bucket, key, limitedInputStream, null);
            cosClient.putObject(req);

        } catch (IOException e) {
            throw new RuntimeException("图片上传失败：" + e.getMessage());
        }

        return key;
    }

    /**
     * 根据 COS 的文件 key 获取访问 URL
     * 开发环境：共有读，直接返回固定 URL
     * 生产环境：私有读写，返回限时签名 URL
     */
    public String getImageUrl(String key) {
        // 空 key 直接返回空
        ExcUtils.throwIfTrue(key == null || key.isEmpty(), "文件key不能为空");

        if (isPublic) {
            // 开发环境：公共读，直接拼接URL
            return "https://" + bucket + ".cos." + region + ".myqcloud.com/" + key;
        } else {
            // 生产环境：私有读写 → 生成限时签名URL
            Date expirationDate = new Date(System.currentTimeMillis() + URL_EXPIRE_SECONDS * 1000);

            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key)
                    .withExpiration(expirationDate);
            URL url = cosClient.generatePresignedUrl(request);
            return url.toString();
        }
    }

    /**
     * 上传图片并返回 url
     * 
     * @param file 图片文件
     * @return 图片 url
     */
    public String uploadAndGetImageUrl(MultipartFile file) {
        String key = uploadPicture(file);
        return getImageUrl(key);
    }

    /**
     * 直接上传图片字节数组到COS（用于服务端图片处理后上传）
     * 
     * @param imageBytes 图片字节数组
     * @param suffix     文件后缀，如 ".png" ".jpg" ".webp"
     * @return cos文件key
     */
    public String uploadBytes(byte[] imageBytes, String suffix) {
        ExcUtils.throwIfTrue(imageBytes == null || imageBytes.length == 0, "图片字节不能为空");
        ExcUtils.throwIfTrue(suffix == null || !suffix.startsWith("."), "文件后缀不合法");
        String uuidFileName = UUID.randomUUID().toString(true) + suffix;
        String key = UPLOAD_PREFIX + System.currentTimeMillis() + "_" + uuidFileName;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes)) {
            PutObjectRequest request = new PutObjectRequest(bucket, key, inputStream, null);
            cosClient.putObject(request);
        } catch (IOException e) {
            throw new RuntimeException("图片上传失败：" + e.getMessage());
        }
        return key;
    }

    /**
     * 根据 COS 的文件 key 删除文件
     * 
     * @param key 文件唯一标识
     */
    public void deletePicture(String key) {
        ExcUtils.throwIfTrue(key == null || key.isEmpty(), "文件key不能为空");
        try {
            cosClient.deleteObject(bucket, key);
        } catch (Exception e) {
            throw new RuntimeException("图片删除失败：" + e.getMessage());
        }
    }

    /**
     * 根据 COS 的文件 key 删除文件
     * 
     * @param allUrl 文件唯一标识
     */
    public void deletePictureByUrl(String allUrl) {
        ExcUtils.throwIfTrue(allUrl == null || allUrl.isEmpty(), "文件key不能为空");
        int length = url.length();
        String key = allUrl.substring(length);
        try {
            cosClient.deleteObject(bucket, key);
        } catch (Exception e) {
            throw new RuntimeException("图片删除失败：" + e.getMessage());
        }
    }

    /**
     * 根据 key 获取图片信息
     * 
     * @param key 文件唯一标识
     * @return 图片信息
     */
    public PictureMessage getPictureMessage(String key) {
        // 1. 构建获取图片信息的请求
        GetObjectRequest getObj = new GetObjectRequest(bucket, key);
        // 固定参数：获取图片基础信息
        getObj.putCustomQueryParameter("imageInfo", null);

        // 2. 获取 COS 返回的流
        COSObject cosObject = cosClient.getObject(getObj);

        PictureMessage pictureMessage = new PictureMessage();

        try (COSObjectInputStream inputStream = cosObject.getObjectContent()) {
            // 3. 把流转成字符串（腾讯云返回 JSON 格式的图片信息）
            String imageInfoJson = IoUtil.readUtf8(inputStream);
            log.info("图片信息 JSON：{}", imageInfoJson);

            // 4. 转成 Map/对象，方便拿宽、高、格式
            Map<String, Object> imageInfo = JSONUtil.parseObj(imageInfoJson);

            String width = String.valueOf(imageInfo.get("width")); // 宽
            String height = String.valueOf(imageInfo.get("height")); // 高
            String size = String.valueOf(imageInfo.get("size")); // 文件大小 byte

            pictureMessage.setWidth(width);
            pictureMessage.setHeight(height);
            pictureMessage.setSize(size);

        } catch (Exception e) {
            throw new RuntimeException("获取图片信息失败", e);
        }

        String[] name = key.split("/")[1].split("\\.");
        String url = this.getImageUrl(key);
        pictureMessage.setUrl(url);
        pictureMessage.setPictureName(name[0]);
        return pictureMessage;
    }
}