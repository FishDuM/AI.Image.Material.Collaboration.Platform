package hk.ljx.fishpicsbackend.common.constants;

/**
 * 用户常量
 */
public interface UserConstants {

    // 验证码
    String CHECK_CODE = "data:image/png;base64,";

    // 密码长度
    int PASSWORD_MIN_LENGTH = 8;
    int PASSWORD_MAX_LENGTH = 32;

    // 个人信息类
    String DEFAULT_NICK_NAME = "小鱼籽_";

    // 拼接验证码
    static String getCheckCode(String captchaKey) {
        return CHECK_CODE + captchaKey;
    }
}
