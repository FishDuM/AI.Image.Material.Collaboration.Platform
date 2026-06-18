package hk.ljx.fishpicsbackend.common.infra;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.*;
import hk.ljx.fishpicsbackend.common.exception.BaseException;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.picture.dto.PictureMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;

import java.io.InputStream;
import java.net.URL;
import java.util.*;

@Slf4j
@Service
public class CosService {

    @Resource
    private COSClient cosClient;

    @Value("${cos.region}")
    private String region;

    @Value("${cos.bucket}")
    private String bucket;

    @Value("${cos.url}")
    private String url;

    /**
     * 上传路径前缀
     */
    private static final String UPLOAD_PREFIX = "picture/";

    /**
     * 生成唯一的 COS key
     */
    public String generateKey() {
        return UPLOAD_PREFIX + System.currentTimeMillis() + "_" + UUID.randomUUID().toString(true) + ".webp";
    }

    /**
     * 上传图片到腾讯云COS（MultipartFile 上传）
     * 纯 COS 操作，不含业务校验
     *
     * @param file 前端上传的文件
     * @return cos文件唯一key
     */
    public String uploadPicture(MultipartFile file) {
        ExcUtils.throwIfTrue(file == null || file.isEmpty(), "上传文件不能为空");

        String key = generateKey();

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());

            PutObjectResult result = cosClient.putObject(bucket, key, inputStream, metadata);
            ExcUtils.throwIfTrue(result == null, "上传文件失败");
        } catch (Exception e) {
            log.error("上传文件失败", e);
            ExcUtils.error(ExceptionCode.INTERNAL_SERVER_ERROR, "上传文件失败");
        }
        return key;
    }

    /**
     * 上传图片到腾讯云COS（InputStream 上传，用于 URL 下载等场景）
     *
     * @param inputStream   图片输入流
     * @param contentLength 图片字节数
     * @return cos文件唯一key
     */
    public String uploadPicture(InputStream inputStream, long contentLength) {
        String key = UPLOAD_PREFIX + System.currentTimeMillis() + "_" + UUID.randomUUID().toString(true) + ".webp";
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(contentLength);

            PutObjectResult result = cosClient.putObject(
                    bucket,
                    key,
                    inputStream,
                    metadata
            );
            ExcUtils.throwIfTrue(result == null, "上传文件失败");
        } catch (Exception e) {
            log.error("上传文件失败", e);
            ExcUtils.error(ExceptionCode.INTERNAL_SERVER_ERROR, "上传文件失败");
        }
        return key;
    }

    /**
     * 根据 COS 的文件 key 获取访问 URL
     */
    public String getImageUrl(String key) {
        ExcUtils.throwIfTrue(key == null || key.isEmpty(), "文件key不能为空");
        return "https://" + bucket + ".cos." + region + ".myqcloud.com/" + key;
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
     * 根据 COS 的文件 key 删除文件
     * 
     * @param key 文件唯一标识
     */
    public void deletePicture(String key) {
        ExcUtils.throwIfTrue(key == null || key.isEmpty(), "文件key不能为空");
        try {
            cosClient.deleteObject(bucket, key);
        } catch (Exception e) {
            log.error("COS 删除文件失败: key={}", key, e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "图片删除失败，请稍后重试");
        }
    }

    /**
     * 根据完整 URL 删除 COS 文件
     * 校验 URL host 等于当前 bucket，防止通过拼接 URL 误删其他 bucket 的文件
     */
    public void deletePictureByUrl(String allUrl) {
        ExcUtils.throwIfTrue(allUrl == null || allUrl.isEmpty(), "文件URL不能为空");
        // 使用 URI 解析提取路径，避免依赖配置 URL 的精确格式
        try {
            java.net.URI uri = java.net.URI.create(allUrl);
            // host 必须等于当前配置的 bucket 域名
            String host = uri.getHost();
            String configuredUrl = (this.url != null && !this.url.isBlank()) ? this.url : null;
            String expectedHost1 = bucket + ".cos." + region + ".myqcloud.com";
            // 解析 configuredUrl 的 host，与入参 host 做 equals 校验
            boolean hostValid = host != null
                    && (host.equalsIgnoreCase(expectedHost1)
                    || (configuredUrl != null && host.equalsIgnoreCase(parseHost(configuredUrl))));
            ExcUtils.throwIfTrue(!hostValid, ExceptionCode.FORBIDDEN,
                    "URL 域名与当前 COS 配置不匹配");
            String key = uri.getPath();
            if (key != null && key.startsWith("/")) {
                key = key.substring(1);
            }
            ExcUtils.throwIfTrue(key == null || key.isEmpty(), "URL格式不正确，无法提取文件key");
            cosClient.deleteObject(bucket, key);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("COS 通过URL删除文件失败: url={}", allUrl, e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "图片删除失败，请稍后重试");
        }
    }

    /**
     * 根据 COS 的文件 key 获取图片信息（宽高、大小、格式）
     * 优先使用 imageInfo 查询参数（不下载完整图片）获取精确元数据；
     * 如果失败（文件不存在、格式不支持等），降级为仅获取文件大小
     */
    public PictureMetadata getPictureMetadata(String key) {
        PictureMetadata metadata = new PictureMetadata();

        try {
            // 优先使用 imageInfo 查询参数，只返回 JSON 元数据，不下载完整图片
            GetObjectRequest getObj = new GetObjectRequest(bucket, key);
            getObj.putCustomQueryParameter("imageInfo", null);
            COSObject cosObject = cosClient.getObject(getObj);
            if (cosObject == null) {
                throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "COS 返回为空: " + key);
            }

            try (COSObjectInputStream inputStream = cosObject.getObjectContent()) {
                String imageInfoJson = IoUtil.readUtf8(inputStream);
                log.info("图片信息 JSON：{}", imageInfoJson);
                Map<String, Object> imageInfo = JSONUtil.parseObj(imageInfoJson);
                metadata.setWidth(String.valueOf(imageInfo.get("width")));
                metadata.setHeight(String.valueOf(imageInfo.get("height")));
                metadata.setSize(String.valueOf(imageInfo.get("size")));
            }
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            // 降级：仅获取文件大小（不下载文件内容）
            log.warn("获取图片元数据失败，降级为仅获取文件大小: key={}", key, e);
            try {
                ObjectMetadata objectMetadata = cosClient.getObjectMetadata(bucket, key);
                if (objectMetadata == null) {
                    throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "COS 文件不存在: " + key);
                }
                metadata.setSize(String.valueOf(objectMetadata.getContentLength()));
            } catch (BaseException be) {
                throw be;
            } catch (Exception ex) {
                log.error("获取图片信息最终失败: key={}", key, ex);
                throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "获取图片信息失败");
            }
        }

        String[] parts = key.split("/");
        String fileName = parts[parts.length - 1];
        String[] nameParts = fileName.split("\\.");
        metadata.setUrl(this.getImageUrl(key));
        metadata.setPictureName(nameParts[0]);
        return metadata;
    }

    // ==================== 预签名 URL ====================

    /**
     * 生成 COS 预签名 URL（用于分享场景）
     *
     * @param key             COS 文件 key
     * @param expirationSeconds 有效期（秒）
     * @return 预签名 URL
     */
    public String getPresignedUrl(String key, int expirationSeconds) {
        ExcUtils.throwIfTrue(key == null || key.isEmpty(), "文件key不能为空");
        Date expiration = new Date(System.currentTimeMillis() + expirationSeconds * 1000L);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key);
        request.setExpiration(expiration);
        request.setMethod(HttpMethodName.GET);
        URL presignedUrl = cosClient.generatePresignedUrl(request);
        return presignedUrl.toString();
    }

    // ==================== 分片上传方法 ====================

    /**
     * 初始化分片上传
     *
     * @param cosKey COS 存储路径
     * @return uploadId
     */
    public byte[] getObjectBytes(String key) {
        ExcUtils.throwIfTrue(key == null || key.isEmpty(), "文件key不能为空");
        try (COSObject cosObject = cosClient.getObject(bucket, key);
             COSObjectInputStream inputStream = cosObject.getObjectContent()) {
            return IoUtil.readBytes(inputStream);
        } catch (Exception e) {
            log.error("读取 COS 文件失败: key={}", key, e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "读取文件失败");
        }
    }

    public String getObjectContentType(String key) {
        ExcUtils.throwIfTrue(key == null || key.isEmpty(), "文件key不能为空");
        try {
            ObjectMetadata metadata = cosClient.getObjectMetadata(bucket, key);
            String contentType = metadata != null ? metadata.getContentType() : null;
            return (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
        } catch (Exception e) {
            log.error("读取 COS 文件类型失败: key={}", key, e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "读取文件信息失败");
        }
    }

    public String initiateMultipartUpload(String cosKey) {
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucket, cosKey);
        InitiateMultipartUploadResult result = cosClient.initiateMultipartUpload(request);
        return result.getUploadId();
    }

    /**
     * 上传单个分片
     *
     * @param cosKey      COS 存储路径
     * @param uploadId    分片上传 ID
     * @param partNumber  分片编号（从 1 开始）
     * @param inputStream 分片数据流
     * @param partSize    分片大小
     * @return ETag
     */
    public String uploadPart(String cosKey, String uploadId, int partNumber,
                             InputStream inputStream, long partSize) {
        UploadPartRequest request = new UploadPartRequest();
        request.setBucketName(bucket);
        request.setKey(cosKey);
        request.setUploadId(uploadId);
        request.setPartNumber(partNumber);
        request.setInputStream(inputStream);
        request.setPartSize(partSize);
        UploadPartResult result = cosClient.uploadPart(request);
        return result.getETag();
    }

    /**
     * 完成分片上传（合并分片）
     *
     * @param cosKey      COS 存储路径
     * @param uploadId    分片上传 ID
     * @param partETags   分片 ETag 列表（需按分片编号排序）
     */
    public void completeMultipartUpload(String cosKey, String uploadId,
                                        List<PartETag> partETags) {
        CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(
                bucket, cosKey, uploadId, partETags);
        cosClient.completeMultipartUpload(request);
    }

    /**
     * 放弃未完成的分片上传，防止 COS 端残留 multipart session
     */
    public void abortMultipartUpload(String cosKey, String uploadId) {
        try {
            AbortMultipartUploadRequest request = new AbortMultipartUploadRequest(bucket, cosKey, uploadId);
            cosClient.abortMultipartUpload(request);
            log.info("COS 分片上传已放弃: cosKey={}", cosKey);
        } catch (Exception e) {
            log.warn("放弃 COS 分片上传失败(可忽略,COS 会自动清理): cosKey={}, err={}", cosKey, e.getMessage());
        }
    }

    /**
     * V12-#8:从配置 url 字符串解析 host
     */
    private static String parseHost(String url) {
        if (url == null) return null;
        try {
            String s = url.trim();
            if (!s.contains("://")) {
                // 裸 host 或 host:port
                int slash = s.indexOf('/');
                return slash >= 0 ? s.substring(0, slash) : s;
            }
            java.net.URI u = java.net.URI.create(s);
            String h = u.getHost();
            if (h == null && u.getAuthority() != null) {
                // URI 解析失败时回退到 authority
                String auth = u.getAuthority();
                int at = auth.indexOf('@');
                h = at >= 0 ? auth.substring(at + 1) : auth;
            }
            return h;
        } catch (Exception e) {
            return null;
        }
    }
}
