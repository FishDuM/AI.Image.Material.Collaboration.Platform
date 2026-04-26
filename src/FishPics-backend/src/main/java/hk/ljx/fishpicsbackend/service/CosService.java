package hk.ljx.fishpicsbackend.service;

import cn.hutool.core.lang.UUID;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.PutObjectRequest;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.utils.LimitedInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Service
public class CosService {

    @Resource
    private COSClient cosClient;

    @Value("${cos.region}")
    private String region;

    @Value("${cos.bucket}")
    private String bucket;

    @Value("${cos.public:false}")
    private boolean isPublic;

    // ====================== 配置常量 ======================
    /**
     * 最大文件大小 5MB
     */
    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    /**
     * 允许上传的图片类型
     */
    private static final List<String> ALLOWED_IMG_TYPES = Arrays.asList(
            "image/jpeg",
            "image/png",
            "image/jpg",
            "image/gif",
            "image/webp"
    );

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

        // 2. 校验是否为图片类型
        String contentType = file.getContentType();
        ExcUtils.throwIfTrue(!ALLOWED_IMG_TYPES.contains(contentType),
                "只能上传 JPG、PNG、GIF、WEBP 格式的图片");

        // 3. 校验文件名合法性
        String originalFilename = file.getOriginalFilename();
        ExcUtils.throwIfTrue(originalFilename == null || !originalFilename.contains("."),
                "文件名不合法，请上传带后缀的图片");

        // 4. 生成唯一文件路径（防止重复 + 安全）
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        String uuidFileName = UUID.randomUUID().toString(true) + suffix;
        String key = UPLOAD_PREFIX + System.currentTimeMillis() + "_" + uuidFileName;

        // 5. 流式上传COS（自动大小限制，不落地）
        try (InputStream inputStream = file.getInputStream();
             LimitedInputStream limitedInputStream = new LimitedInputStream(inputStream, MAX_SIZE)) {

            PutObjectRequest request = new PutObjectRequest(bucket, key, limitedInputStream, null);
            cosClient.putObject(request);

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
}