package hk.ljx.fishpicsbackend.common.constants;

/**
 * Redis key constants.
 */
public interface RedisConstants {

    String LOGIN_CODE_KEY = "LOGIN_CODE";
    String REGISTER_CODE_KEY = "REGISTER_CODE";
    String USER_ID_KEY = "USER_ID:";
    long USER_TOKEN_INVALID_TTL_DAYS = 7;

    String JWT_BLACKLIST_KEY = "JWT_BLACKLIST:";
    String USER_TOKEN_INVALID_BEFORE_KEY = "USER_TOKEN_INVALID_BEFORE:";

    String FILE_UPLOAD_CHUNKS_KEY = "file:upload:";
    String FILE_UPLOAD_ID_KEY = "file:uploadid:";
    String FILE_CHUNK_ETAG_KEY = "file:chunk_etag:";
    String FILE_CHUNK_SIZE_KEY = "file:chunk_size:";
    String FILE_COS_KEY = "file:coskey:";
    String FILE_UPLOAD_OWNER_KEY = "file:owner:";
    String FILE_MERGE_RESULT_KEY = "file:merge_result:";
    long FILE_UPLOAD_TTL = 24;

    String USER_PERMISSIONS_KEY = "USER_PERMISSIONS:";
    String BANNED_USERS_KEY = "BANNED_USERS";

    static String getRegisterCodeKey(String str) {
        return REGISTER_CODE_KEY + str;
    }

    static String getLoginCodeKey(String str) {
        return LOGIN_CODE_KEY + str;
    }

    static String getUserIdKey(String token) {
        return USER_ID_KEY + token;
    }

    static String getJwtBlacklistKey(String jti) {
        return JWT_BLACKLIST_KEY + jti;
    }

    static String getUserTokenInvalidBeforeKey(Long userId) {
        return USER_TOKEN_INVALID_BEFORE_KEY + userId;
    }

    // upload 会话 key 加上 userId 维度
    // 原 key 用纯 md5,改成 userId:md5 联合键,确保每个用户的上传会话完全隔离
    static String getUserFileUploadOwnerKey(Long userId, String md5) {
        return FILE_UPLOAD_OWNER_KEY + userId + ":" + md5;
    }

    // 上传会话数据键也加 userId 隔离
    static String getFileUploadChunksKey(Long userId, String md5) {
        return FILE_UPLOAD_CHUNKS_KEY + userId + ":" + md5;
    }

    static String getFileUploadIdKey(Long userId, String md5) {
        return FILE_UPLOAD_ID_KEY + userId + ":" + md5;
    }

    static String getFileChunkEtagKey(Long userId, String md5) {
        return FILE_CHUNK_ETAG_KEY + userId + ":" + md5;
    }

    static String getFileChunkSizeKey(Long userId, String md5) {
        return FILE_CHUNK_SIZE_KEY + userId + ":" + md5;
    }

    static String getFileCosKeyKey(Long userId, String md5) {
        return FILE_COS_KEY + userId + ":" + md5;
    }

    static String getFileMergeResultKey(Long userId, String md5) {
        return FILE_MERGE_RESULT_KEY + userId + ":" + md5;
    }
}
