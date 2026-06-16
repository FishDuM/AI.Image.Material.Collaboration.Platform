package hk.ljx.fishpicsbackend.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    String module() default "";
    String operation() default "";
    String description() default "";
}
