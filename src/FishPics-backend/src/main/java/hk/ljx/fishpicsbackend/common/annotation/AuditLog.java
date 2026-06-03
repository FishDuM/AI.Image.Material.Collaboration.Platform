package hk.ljx.fishpicsbackend.common.annotation;

import java.lang.annotation.*;

/**
 * 审计日志注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    // 操作模块
    String module() default "";

    // 操作类型
    String operation() default "";

    // 操作描述
    String description() default "";
}
