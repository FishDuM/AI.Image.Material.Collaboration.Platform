package hk.ljx.fishpicsbackend.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 要求管理员权限
 * 只有 role = 1 的用户才能访问
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAdmin {
    /**
     * 错误提示信息
     */
    String message() default "需要管理员权限";
}
