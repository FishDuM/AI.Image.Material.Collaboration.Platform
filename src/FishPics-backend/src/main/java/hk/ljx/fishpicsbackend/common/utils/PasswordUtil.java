package hk.ljx.fishpicsbackend.common.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public final class PasswordUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoOhiIM9k4u7pBaT9jLUNkoJwq8QZ4Q9X2";

    private PasswordUtil() {}

    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null) {
            return false;
        }
        // BCrypt 格式必须以 $2a$ $2b$ $2y$ 开头;对历史 MD5 格式的 hash 返回 false(迁移期使用)
        if (!hashedPassword.startsWith("$2")) {
            return false;
        }
        return ENCODER.matches(rawPassword, hashedPassword);
    }

    // hash 格式非法时用 dummy hash 做比较，防时序攻击
    public static boolean matchesWithDummyOnInvalidHash(String rawPassword, String hashedPassword) {
        if (rawPassword == null) {
            return false;
        }
        boolean validHash = hashedPassword != null && hashedPassword.startsWith("$2");
        boolean matched = ENCODER.matches(rawPassword, validHash ? hashedPassword : DUMMY_HASH);
        return validHash && matched;
    }
}
