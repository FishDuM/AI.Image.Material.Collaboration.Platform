package hk.ljx.fishpicsbackend.common.constants;

/**
 * Redis 键常量
 */
public interface RedisConstants {
// --------------------------------------------- 用户类----------------------------------------------

    // 注册验证码前缀
    String REGISTER_USER_PREFIX = "REGISTER_CHECK_CODE:";
    // 登录验证码前缀
    String LOGIN_USER_PREFIX = "LOGIN_CHECK_CODE:";

    // 注册整体验证码 key
    static String getCheckCodeKeyByRegister(String str) {
        return REGISTER_USER_PREFIX + str;
    }

    // 登录整体验证码 key
    static String getCheckCodeKeyByLogin(String str) {
        return LOGIN_USER_PREFIX + str;
    }
}
