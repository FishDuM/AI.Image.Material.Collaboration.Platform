package hk.ljx.fishpicsbackend.picture.dto;

import lombok.Data;

/**
 * 合并分片请求
 */
@Data
public class MergeChunksRequest {
    /** 文件 MD5 */
    private String md5;
    /** 文件大小(Byte) */
    private Long size;
    /** COS 存储路径 */
    private String cosKey;
    /** 总分片数 */
    private Integer totalChunks;
    /** 目标空间ID（可选） */
    private Long targetSpaceId;
}
