package hk.ljx.fishpicsbackend.common.utils;

import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.user.entity.User;

public final class UserHolder {

    private UserHolder() {}

    private static final ThreadLocal<LoginContext> CONTEXT_HOLDER = new ThreadLocal<>();

    public static void setLoginContext(LoginContext context) {
        CONTEXT_HOLDER.set(context);
    }

    public static LoginContext getLoginContext() {
        return CONTEXT_HOLDER.get();
    }

    public static void removeLoginContext() {
        CONTEXT_HOLDER.remove();
    }

    public static User getUser() {
        LoginContext ctx = getLoginContext();
        if (ctx == null) {
            return null;
        }
        User user = new User();
        user.setId(ctx.getUserId());
        user.setUsername(ctx.getUsername());
        user.setNickname(ctx.getNickname());
        user.setAvatar(ctx.getAvatar());
        user.setStatus(ctx.getStatus());
        user.setLevel(ctx.getLevel());
        user.setRole(ctx.getRole());
        return user;
    }
}
