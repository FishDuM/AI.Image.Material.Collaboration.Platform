package hk.ljx.fishpicsbackend.space.enums;

import java.util.List;
import java.util.Map;

public enum TeamMemberRole {
    OWNER(1, "所有者"),
    MEMBER(2, "成员"),
    EDITOR(3, "编辑者"),
    VIEWER(4, "浏览者");

    public static final List<Integer> WRITABLE_ROLE_IDS = List.of(OWNER.code, MEMBER.code);
    public static final Map<Integer, String> ROLE_NAME_MAP = Map.of(
            OWNER.code, OWNER.name,
            MEMBER.code, MEMBER.name,
            EDITOR.code, EDITOR.name,
            VIEWER.code, VIEWER.name
    );

    private final int code;
    private final String name;

    TeamMemberRole(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public int code() {
        return code;
    }

    public static boolean isOwner(Integer roleId) {
        return roleId != null && roleId == OWNER.code;
    }

    public static boolean isOwner(Long roleId) {
        return roleId != null && roleId == OWNER.code;
    }

    public static boolean isWritable(Integer roleId) {
        return roleId != null && WRITABLE_ROLE_IDS.contains(roleId);
    }

    public static boolean isGrantable(Long roleId) {
        return isOwner(roleId) || (roleId != null && roleId == MEMBER.code);
    }

    public static String nameOf(Integer roleId) {
        return ROLE_NAME_MAP.getOrDefault(roleId, "未知角色");
    }
}
