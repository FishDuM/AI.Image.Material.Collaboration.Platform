package hk.ljx.fishpicsbackend.common.constants;

/**
 * Redis 键常量
 */
public interface RedisConstants {
// --------------------------------------------- 用户类----------------------------------------------

    // token类
    String LOGIN_CODE_KEY = "LOGIN_CODE";
    String REGISTER_CODE_KEY = "REGISTER_CODE";
    String USER_ID_KEY = "USER_ID:";
    String USER_MESSAGE_KEY = "USER_MESSAGE:";

    // ==================== 权限上下文 ====================
    // 权限上下文缓存键前缀
    String USER_PERM_CTX_KEY = "USER_PERM_CTX:";
    // 权限上下文缓存TTL（天）
    long USER_PERM_CTX_TTL = 7;

    // ==================== JWT 黑名单 ====================
    String JWT_BLACKLIST_KEY = "JWT_BLACKLIST:";

    // ==================== 分片上传 ====================
    // 已上传分片编号集合
    String FILE_UPLOAD_CHUNKS_KEY = "file:upload:";
    // COS multipartUploadId
    String FILE_UPLOAD_ID_KEY = "file:uploadid:";
    // 分片编号→ETag 映射
    String FILE_CHUNK_ETAG_KEY = "file:chunk_etag:";
    // 分片上传 Redis TTL（小时）
    long FILE_UPLOAD_TTL = 24;

    // 获取注册验证码 key
    static String getRegisterCodeKey(String str) {
        return REGISTER_CODE_KEY + str;
    }

    // 获取登录验证码 key
    static String getLoginCodeKey(String str) {
        return LOGIN_CODE_KEY + str;
    }

    // 根据token获得用户id
    static String getUserIdKey(String token) {
        return USER_ID_KEY + token;
    }

    // 根据用户id获得用户信息
    static String getUserInfoKey(Long userId) {
        return USER_MESSAGE_KEY + userId;
    }

    // 根据用户id获得权限上下文
    static String getUserPermCtxKey(Long userId) {
        return USER_PERM_CTX_KEY + userId;
    }

    // 获取 JWT 黑名单 key
    static String getJwtBlacklistKey(String jti) {
        return JWT_BLACKLIST_KEY + jti;
    }

    // 获取分片上传已上传分片集合 key
    static String getFileUploadChunksKey(String md5) {
        return FILE_UPLOAD_CHUNKS_KEY + md5;
    }

    // 获取分片上传 uploadId key
    static String getFileUploadIdKey(String md5) {
        return FILE_UPLOAD_ID_KEY + md5;
    }

    // 获取分片 ETag 映射 key
    static String getFileChunkEtagKey(String md5) {
        return FILE_CHUNK_ETAG_KEY + md5;
    }
}
