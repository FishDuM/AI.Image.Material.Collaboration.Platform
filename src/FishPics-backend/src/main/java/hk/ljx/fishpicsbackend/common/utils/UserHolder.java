package hk.ljx.fishpicsbackend.common.utils;

import hk.ljx.fishpicsbackend.user.User;

public class UserHolder {
    private static final ThreadLocal<User> USER_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * 设置用户到线程
     * @param user 用户
     */
    public static void setUser(User user) {
        USER_THREAD_LOCAL.set(user);
    }

    /**
     * 获取线程中的用户
     * @return 用户，未登录则返回null
     */
    public static User getUser() {
        return USER_THREAD_LOCAL.get();
    }

    /**
     * 清除线程中的用户
     */
    public static void removeUser() {
        USER_THREAD_LOCAL.remove();
    }
}
