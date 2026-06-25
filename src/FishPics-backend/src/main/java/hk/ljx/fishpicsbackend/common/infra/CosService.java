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

    private static final String UPLOAD_PREFIX = "picture/";

    // timestamp_uuid.webp
    public String generateKey() {
        return UPLOAD_PREFIX + System.currentTimeMillis() + "_" + UUID.randomUUID().toString(true) + ".webp";
    }

    // 纯 COS 上传，不含业务校验
    public String uploadPicture(MultipartFile file) {
        ExcUtils.throwIfTrue(file == null || file.isEmpty(), "上传文件不能为空");

        String key = generateKey();

        try (InputStream inputStream = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());

            PutObjectResult result = cosClient.putObject(bucket, key, inputStream, metadata);
            ExcUtils.throwIfTrue(result == null, "上传文件失败");
        } catch (Exception e) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "上传文件失败", e);
        }
        return key;
    }

    // InputStream 上传（URL 下载等场景用）
    public String uploadPicture(InputStream inputStream, long contentLength) {
        String key = UPLOAD_PREFIX + System.currentTimeMillis() + "_" + UUID.randomUUID().toString(true) + ".webp";
        try (inputStream) {
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
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "上传文件失败", e);
        }
        return key;
    }

    private void requireKey(String key) {
        ExcUtils.throwIfTrue(key == null || key.isEmpty(), "文件key不能为空");
    }

    public String getImageUrl(String key) {
        requireKey(key);
        return "https://" + bucket + ".cos." + region + ".myqcloud.com/" + key;
    }

    public String uploadAndGetImageUrl(MultipartFile file) {
        String key = uploadPicture(file);
        return getImageUrl(key);
    }

    public void deletePicture(String key) {
        requireKey(key);
        try {
            cosClient.deleteObject(bucket, key);
        } catch (Exception e) {
            log.error("COS 删除文件失败: key={}", key, e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "图片删除失败，请稍后重试");
        }
    }

    public void deletePictureByUrl(String allUrl) {
        ExcUtils.throwIfTrue(allUrl == null || allUrl.isEmpty(), "文件URL不能为空");
        try {
            java.net.URI uri = java.net.URI.create(allUrl);
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

    public PictureMetadata getPictureMetadata(String key) {
        PictureMetadata metadata = new PictureMetadata();

        try {
            fetchImageInfo(key, metadata);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.warn("获取图片元数据失败，降级为仅获取文件大小: key={}", key, e);
            fetchFileSizeOnly(key, metadata);
        }

        if (metadata.getSize() == null) {
            try {
                ObjectMetadata objectMetadata = cosClient.getObjectMetadata(bucket, key);
                if (objectMetadata != null) {
                    metadata.setSize(objectMetadata.getContentLength());
                }
            } catch (Exception ex) {
                log.warn("降级获取文件大小失败: key={}", key, ex);
            }
        }

        String[] parts = key.split("/");
        String fileName = parts[parts.length - 1];
        String[] nameParts = fileName.split("\\.");
        metadata.setUrl(this.getImageUrl(key));
        metadata.setPictureName(nameParts[0]);
        return metadata;
    }

    private void fetchImageInfo(String key, PictureMetadata metadata) throws Exception {
        GetObjectRequest getObj = new GetObjectRequest(bucket, key);
        getObj.putCustomQueryParameter("imageInfo", null);
        COSObject cosObject = cosClient.getObject(getObj);
        if (cosObject == null) {
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "COS 返回为空: " + key);
        }

        try (COSObjectInputStream inputStream = cosObject.getObjectContent()) {
            String imageInfoJson = IoUtil.readUtf8(inputStream);
            Map<String, Object> imageInfo = JSONUtil.parseObj(imageInfoJson);
            metadata.setWidth(String.valueOf(imageInfo.get("width")));
            metadata.setHeight(String.valueOf(imageInfo.get("height")));
            Object sizeVal = imageInfo.get("size");
            if (sizeVal instanceof Number) {
                metadata.setSize(((Number) sizeVal).longValue());
            } else if (sizeVal instanceof String) {
                try {
                    metadata.setSize(Long.parseLong((String) sizeVal));
                } catch (NumberFormatException ignored) {
                    // size 字符串无法解析，后续降级处理
                }
            }
        }
    }

    private void fetchFileSizeOnly(String key, PictureMetadata metadata) {
        try {
            ObjectMetadata objectMetadata = cosClient.getObjectMetadata(bucket, key);
            if (objectMetadata == null) {
                throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "COS 文件不存在: " + key);
            }
            metadata.setSize(objectMetadata.getContentLength());
        } catch (BaseException be) {
            throw be;
        } catch (Exception ex) {
            log.error("获取图片信息最终失败: key={}", key, ex);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "获取图片信息失败");
        }
    }

    public String getPresignedUrl(String key, int expirationSeconds) {
        requireKey(key);
        Date expiration = new Date(System.currentTimeMillis() + expirationSeconds * 1000L);
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, key);
        request.setExpiration(expiration);
        request.setMethod(HttpMethodName.GET);
        URL presignedUrl = cosClient.generatePresignedUrl(request);
        return presignedUrl.toString();
    }

    public byte[] getObjectBytes(String key) {
        requireKey(key);
        try (COSObject cosObject = cosClient.getObject(bucket, key);
             COSObjectInputStream inputStream = cosObject.getObjectContent()) {
            return IoUtil.readBytes(inputStream);
        } catch (Exception e) {
            log.error("读取 COS 文件失败: key={}", key, e);
            throw new BaseException(ExceptionCode.INTERNAL_SERVER_ERROR, "读取文件失败");
        }
    }

    public String getObjectContentType(String key) {
        requireKey(key);
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

    public void completeMultipartUpload(String cosKey, String uploadId,
                                        List<PartETag> partETags) {
        CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(
                bucket, cosKey, uploadId, partETags);
        cosClient.completeMultipartUpload(request);
    }

    public void abortMultipartUpload(String cosKey, String uploadId) {
        try {
            AbortMultipartUploadRequest request = new AbortMultipartUploadRequest(bucket, cosKey, uploadId);
            cosClient.abortMultipartUpload(request);
            log.info("COS 分片上传已放弃: cosKey={}", cosKey);
        } catch (Exception e) {
            log.warn("放弃 COS 分片上传失败(可忽略,COS 会自动清理): cosKey={}, err={}", cosKey, e.getMessage());
        }
    }

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
