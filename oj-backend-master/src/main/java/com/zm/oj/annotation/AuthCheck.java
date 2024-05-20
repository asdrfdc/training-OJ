package com.zm.oj.annotation;

import java.lang.ElementType;
import java.lang.Retention;
import java.lang.RetentionPolicy;
import java.lang.Target;

/**
 * 权限校验
 *
 *
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 必须有某个角色
     *
     * @return
     */
    String mustRole() default "";

}

