package hk.ljx.fishpicsbackend.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Permission check annotation (new version).
 * Supports three layers: system, space, resource.
 *
 * Usage:
 * - @RequirePerm("system:user:manage") - system permission
 * - @RequirePerm("resource:upload") - team permission (needs spaceId param)
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {

    /**
     * Required permission key, e.g. "system:user:manage"
     */
    String value();

    /**
     * spaceId request parameter name.
     * Default: "spaceId"
     */
    String spaceIdParam() default "spaceId";
}
