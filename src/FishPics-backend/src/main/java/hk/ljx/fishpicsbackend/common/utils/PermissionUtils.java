package hk.ljx.fishpicsbackend.common.utils;

import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.user.entity.User;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 权限工具类：根据用户等级构建权限列表和 LoginContext
 */
public final class PermissionUtils {

    private PermissionUtils() {}

    public static LoginContext buildLoginContext(User user) {
        boolean isAdmin = user.getLevel() != null && user.getLevel() >= 3;
        List<String> permissions = getPermissionsByLevel(user.getLevel());
        List<String> systemPerms = permissions.stream()
                .filter(permission -> permission.startsWith("system:"))
                .collect(Collectors.toList());
        List<String> vipPerms = permissions.stream()
                .filter(permission -> !permission.startsWith("system:"))
                .collect(Collectors.toList());

        return LoginContext.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .level(user.getLevel())
                .isAdmin(isAdmin)
                .systemPerms(systemPerms)
                .vipPerms(vipPerms)
                .build();
    }

    public static List<String> getPermissionsByLevel(Integer level) {
        List<String> permissions = new ArrayList<>();
        if (level == null) {
            return permissions;
        }
        if (level >= 3) {
            permissions.add("system:user:manage");
            permissions.add("system:team:manage");
            permissions.add("system:ai:manage");
            permissions.add("system:log:manage");
            permissions.add("system:config");
            permissions.add("space:create");
            permissions.add("space:manage");
            permissions.add("picture:upload");
            permissions.add("picture:manage");
        } else if (level >= 2) {
            permissions.add("space:create");
            permissions.add("picture:upload");
            permissions.add("picture:manage");
            permissions.add("ai:advanced");
        } else if (level >= 1) {
            permissions.add("space:create");
            permissions.add("picture:upload");
            permissions.add("picture:manage");
        } else {
            permissions.add("space:create");
            permissions.add("picture:upload");
        }
        return permissions;
    }
}
