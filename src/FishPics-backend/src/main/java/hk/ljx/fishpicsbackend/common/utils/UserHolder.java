package hk.ljx.fishpicsbackend.common.utils;

import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.user.entity.User;

/**
 * 用户上下文持有器
 * 存储登录上下文（用户 + 权限）到 ThreadLocal
 */
public class UserHolder {

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
        return user;
    }

    /**
     * 兼容旧代码：设置 User 对象
     * 不推荐使用，请使用 setLoginContext
     *
     * @param user 用户对象
     */
    @Deprecated
    public static void setUser(User user) {
        if (user == null) {
            removeLoginContext();
            return;
        }
        LoginContext ctx = LoginContext.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .status(user.getStatus())
                .level(user.getLevel())
                .build();
        setLoginContext(ctx);
    }

    /**
     * 兼容旧代码：清除
     */
    public static void removeUser() {
        removeLoginContext();
    }
}
