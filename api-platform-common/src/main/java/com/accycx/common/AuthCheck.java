package com.accycx.common;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD) // 这个注解只能用在方法上
@Retention(RetentionPolicy.RUNTIME) // 这个注解在运行时仍然可用，可以通过反射读取
public @interface AuthCheck {

//    必须有某个角色
    String mustRole() default ""; // 需要的角色
}
