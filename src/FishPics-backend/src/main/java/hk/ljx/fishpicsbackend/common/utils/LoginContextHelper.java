package hk.ljx.fishpicsbackend.common.utils;

import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.user.entity.User;

/**
 * Service 层统一获取登录用户上下文，消除各 Service 中重复的 null 检查
 */
public final class LoginContextHelper {

    private LoginContextHelper() {}

    /**
     * 获取当前登录的 LoginContext，未登录则抛异常
     */
    public static LoginContext requireLoginContext() {
        LoginContext ctx = UserHolder.getLoginContext();
        ExcUtils.throwIfTrue(ctx == null || ctx.getUserId() == null, ExceptionCode.NOT_LOGIN);
        return ctx;
    }

    /**
     * 获取当前登录的 User 对象（兼容旧代码），未登录则抛异常
     */
    public static User requireUser() {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null || user.getId() == null, ExceptionCode.NOT_LOGIN);
        return user;
    }
}
