package hk.ljx.fishpicsbackend.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限校验注解
 * 支持按权限码控制，也兼容按角色名控制（降级方案）
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    // 要求的权限码，如 "post:review"
    String permission() default "";

    // 权限匹配模式：AND=必须拥有所有, OR=拥有任一即可
    MatchMode mode() default MatchMode.OR;

    enum MatchMode {
        OR, AND
    }
}
