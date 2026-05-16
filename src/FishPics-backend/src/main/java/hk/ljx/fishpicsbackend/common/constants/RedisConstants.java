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

    // 点赞类
    String LIKE_POST_KEY = "LIKE_POST";

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
}
