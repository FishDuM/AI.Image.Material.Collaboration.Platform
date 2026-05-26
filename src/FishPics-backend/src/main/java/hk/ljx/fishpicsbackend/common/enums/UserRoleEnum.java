package hk.ljx.fishpicsbackend.common.enums;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

@Getter
public enum UserRoleEnum {

    ADMIN("admin", "管理员"),
    USER("user", "普通用户");

    private final String role;
    private final String message;

    UserRoleEnum(String role, String message) {
        this.role = role;
        this.message = message;
    }

    /**
     * 根据 role 获取枚举
     */
    public static UserRoleEnum getEnumByRole(String role) {
        if (StrUtil.isBlank(role)) {
            return null;
        }
        for (UserRoleEnum userRoleEnum : UserRoleEnum.values()) {
            if (userRoleEnum.getRole().equals(role)) {
                return userRoleEnum;
            }
        }
        return null;
    }
}
