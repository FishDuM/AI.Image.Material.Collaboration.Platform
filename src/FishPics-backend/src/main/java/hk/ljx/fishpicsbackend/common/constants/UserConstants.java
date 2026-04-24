package hk.ljx.fishpicsbackend.common.constants;

/**
 * 用户常量
 */
public interface UserConstants {
    // 登录类
    String LOGIN_TOKEN = "LOGIN-";

    // 权限类
    String USER = "user";
    String ADMIN = "admin";

    // 密码加密盐值
    String SALT = "fish";

    // 个人信息类
    String DEFAULT_NICK_NAME = "小鱼籽_";


    // 登录token
    static String getLoginTokenKey(Long userId) {
        return LOGIN_TOKEN + userId;
    }
}
