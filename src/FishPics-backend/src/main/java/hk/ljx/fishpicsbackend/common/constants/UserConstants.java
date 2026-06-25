package hk.ljx.fishpicsbackend.common.constants;

/**
 * 用户常量
 */
public interface UserConstants {

    String CHECK_CODE = "data:image/png;base64,";

    int PASSWORD_MIN_LENGTH = 8;
    int PASSWORD_MAX_LENGTH = 32;

    int USERNAME_MIN_LENGTH = 6;
    int USERNAME_MAX_LENGTH = 30;
    int NICKNAME_MIN_LENGTH = 1;
    int NICKNAME_MAX_LENGTH = 30;

    String DEFAULT_NICK_NAME = "小鱼籽_";

    static String getCheckCode(String captchaKey) {
        return CHECK_CODE + captchaKey;
    }
}
