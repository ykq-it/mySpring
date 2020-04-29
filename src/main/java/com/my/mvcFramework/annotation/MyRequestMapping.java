package com.my.mvcFramework.annotation;

import java.lang.annotation.*;

/**
 * 功能描述：
 * @author ykq
 * @date 2020/4/29 11:21
 * @param
 * @return
 */
@Target({ElementType.METHOD, ElementType.TYPE})    //RequestMapping可以注解类和方法
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MyRequestMapping {
    String value() default "";
}
