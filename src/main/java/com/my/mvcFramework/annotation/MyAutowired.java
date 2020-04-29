package com.my.mvcFramework.annotation;

import java.lang.annotation.*;

/**
 * 功能描述：
 * @author ykq
 * @date 2020/4/29 11:21
 * @param
 * @return
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyAutowired {
    String value() default "";
}
