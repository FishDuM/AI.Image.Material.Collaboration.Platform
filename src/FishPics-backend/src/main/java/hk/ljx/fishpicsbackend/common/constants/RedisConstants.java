package hk.ljx.fishpicsbackend.common.constants;

/**
 * Redis 键常量
 */
public interface RedisConstants {
// --------------------------------------------- 用户类----------------------------------------------

    // token类
    String LOGIN_CODE_KEY = "LOGIN_CODE";
    String REGISTER_CODE_KEY = "REGISTER_CODE";
    String TOKEN_KEY = "TOKEN";

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
}
