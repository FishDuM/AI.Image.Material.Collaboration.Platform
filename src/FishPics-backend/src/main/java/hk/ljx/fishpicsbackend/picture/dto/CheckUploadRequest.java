package hk.ljx.fishpicsbackend.picture.dto;

import lombok.Data;

/**
 * 秒传校验请求
 */
@Data
public class CheckUploadRequest {
    /** 文件 MD5 */
    private String md5;
    /** 文件大小(Byte) */
    private Long size;
    /** 目标空间ID（可选，不传则上传到私人空间） */
    private Long targetSpaceId;
}
