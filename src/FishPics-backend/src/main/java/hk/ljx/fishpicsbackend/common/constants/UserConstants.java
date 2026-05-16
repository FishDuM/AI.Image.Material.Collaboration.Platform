package hk.ljx.fishpicsbackend.common.constants;

/**
 * 用户常量
 */
public interface UserConstants {

    // 权限类
    String USER = "user";
    String ADMIN = "admin";

    // 密码加密盐值
    String SALT = "fish";

    // 验证码
    String CHECK_CODE = "data:image/png;base64,";

    // 个人信息类
    String DEFAULT_NICK_NAME = "小鱼籽_";

    // 拼接验证码
    static String getCheckCode(String captchaKey) {
        return CHECK_CODE + captchaKey;
    }
}
