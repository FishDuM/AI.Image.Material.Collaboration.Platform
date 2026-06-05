package hk.ljx.fishpicsbackend.common.utils;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.picture.dto.PictureMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.Resource;

import java.io.InputStream;
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
            log.error("上传文件失败: {}", e.getMessage());
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
            log.error("上传文件失败{}", e.getMessage());
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
        // 构建获取图片信息的请求
        GetObjectRequest getObj = new GetObjectRequest(bucket, key);
        // 获取图片基础信息
        getObj.putCustomQueryParameter("imageInfo", null);

        // 获取 COS 返回的流
        COSObject cosObject = cosClient.getObject(getObj);

        PictureMessage pictureMessage = new PictureMessage();

        try (COSObjectInputStream inputStream = cosObject.getObjectContent()) {
            // 把流转成字符串
            String imageInfoJson = IoUtil.readUtf8(inputStream);
            log.info("图片信息 JSON：{}", imageInfoJson);

            // 转成 Map/对象，方便拿宽、高、格式
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

    // ==================== 分片上传方法 ====================

    /**
     * 初始化分片上传
     *
     * @param cosKey COS 存储路径
     * @return uploadId
     */
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
}