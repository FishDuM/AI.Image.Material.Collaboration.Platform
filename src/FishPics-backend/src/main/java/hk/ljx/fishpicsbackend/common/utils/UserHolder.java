package hk.ljx.fishpicsbackend.common.utils;

import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.user.entity.User;

/**
 * 用户上下文持有器
 * 存储登录上下文（用户 + 权限）到 ThreadLocal
 */
public final class UserHolder {

    private UserHolder() {}

    private static final ThreadLocal<LoginContext> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置登录上下文到线程
     *
     * @param context 登录上下文
     */
    public static void setLoginContext(LoginContext context) {
        CONTEXT_HOLDER.set(context);
    }

    /**
     * 获取线程中的登录上下文
     *
     * @return 登录上下文，未登录则返回 null
     */
    public static LoginContext getLoginContext() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 清除线程中的登录上下文
     */
    public static void removeLoginContext() {
        CONTEXT_HOLDER.remove();
    }

    // ==================== 兼容性方法 ====================

    /**
     * 兼容旧代码：获取 User 对象
     * 从 LoginContext 中构建一个 User 对象（仅包含基本信息）
     *
     * @return User 对象，未登录则返回 null
     */
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
