package hk.ljx.fishpicsbackend.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 用户角色枚举
 */
@Getter
@AllArgsConstructor
public enum Role {
    NORMAL(0, "普通用户"),
    ADMIN(1, "管理员");

    private final int code;
    private final String desc;

    public static boolean isAdmin(Integer role) {
        return role != null && role == ADMIN.code;
    }
}
