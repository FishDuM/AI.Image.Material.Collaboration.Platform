package hk.ljx.fishpicsbackend.common.utils;

import hk.ljx.fishpicsbackend.common.context.LoginContext;
import hk.ljx.fishpicsbackend.common.exception.ExcUtils;
import hk.ljx.fishpicsbackend.common.exception.ExceptionCode;
import hk.ljx.fishpicsbackend.user.entity.User;

public final class LoginContextHelper {

    private LoginContextHelper() {}

    public static LoginContext requireLoginContext() {
        LoginContext ctx = UserHolder.getLoginContext();
        ExcUtils.throwIfTrue(ctx == null || ctx.getUserId() == null, ExceptionCode.NOT_LOGIN);
        return ctx;
    }

    public static User requireUser() {
        User user = UserHolder.getUser();
        ExcUtils.throwIfTrue(user == null || user.getId() == null, ExceptionCode.NOT_LOGIN);
        return user;
    }
}
