package com.accycx.apiplatforminterface.config;


import com.accycx.apiplatforminterface.interceptor.ApiAuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置类
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Autowired
    private ApiAuthInterceptor apiAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry){
//        将拦截器注册到Spring MVC中
        registry.addInterceptor(apiAuthInterceptor)
                .addPathPatterns("/**");//拦截所有进入此服务的请求
    }
}
