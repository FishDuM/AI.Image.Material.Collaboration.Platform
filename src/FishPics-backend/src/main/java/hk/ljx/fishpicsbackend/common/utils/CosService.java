package hk.ljx.fishpicsbackend.common.utils;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.picture.dto.PictureMessage;
import hk.ljx.fishpicsbackend.user.entity.User;
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

    private static final Set<String> ALLOWED_TYPES = Set.of(
            // 基础图片
            "jpg", "jpeg", "png", "bmp", "webp", "tiff", "gif",
            // 高级现代格式
            "avif", "heic", "heif", "apng", "astc", "tpg",
            // 专业设计格式
            "psd", "ai", "eps",
            // 相机RAW全格式
            "raw", "dng", "cr3", "crw", "mos", "erf", "3fr", "fff",
            "kdc", "dcr", "rw2", "pef", "sr2", "srf", "arw", "nef",
            "nrw", "orf", "mef", "mrw"
    );

    /**
     * 上传路径前缀
     */
    private static final String UPLOAD_PREFIX = "picture/";

    /**
     * 判断文件类型
     * @param file 文件
     * @return 是否是允许的图片格式
     */
    public static String getValidFileType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try (InputStream in = file.getInputStream()) {
            String realType = FileTypeUtil.getType(in);
            if (realType == null) {
                return null;
            }
            realType = realType.toLowerCase();
            return ALLOWED_TYPES.contains(realType) ? realType : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 上传图片到腾讯云COS
     *
     * @param file 前端上传的文件
     * @return cos文件唯一key
     */
    public String uploadPicture(MultipartFile file) {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null || user.getLevel() == null, ExceptionCode.NOT_LOGIN);

        // 校验文件
        ExcUtils.throwIfTrue(file.isEmpty(), "上传文件不能为空");
        String validFileType = getValidFileType(file);
        ExcUtils.throwIfTrue(validFileType == null, "上传文件格式不正确");

        // 生成唯一文件路径
        String key = UPLOAD_PREFIX + System.currentTimeMillis() + "_" + UUID.randomUUID().toString(true) + ".webp";

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
        ExcUtils.throwIfTrue(file.getSize() > size, "上传文件大小不能超过" + size + "字节");

        // 流式上传到 COS
        try (InputStream inputStream = file.getInputStream()){
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());

            PutObjectResult result = cosClient.putObject(
                    bucket,
                    key,
                    inputStream,
                    metadata
            );
            ExcUtils.throwIfTrue(result == null, "上传文件失败");
        }catch (Exception e) {
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
}