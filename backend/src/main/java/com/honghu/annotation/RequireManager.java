package com.honghu.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注只有管理员（type=manager）才能访问的接口。
 *
 * <p>取代原先「URI 以 /api/admin/ 开头」的字符串前缀判断：前缀判断依赖控制器
 * 作者始终把管理端接口挂到该路径下，一旦有人换个路径就绕过了鉴权。改成方法/类级
 * 注解后，授权要求写在接口本身，由 AuthInterceptor 读取 HandlerMethod 判断，
 * 与路径无关。可标注在方法或整个控制器类上。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireManager {
}
