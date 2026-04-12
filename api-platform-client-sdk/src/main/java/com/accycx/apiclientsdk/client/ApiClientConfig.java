package com.accycx.apiclientsdk.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * ApiClient 自动配置类
 */
@Configuration
// 这个注解的意思是：去 application.yml 里读取前缀为 "api.client" 的配置，映射到这个类的属性上
@ConfigurationProperties("api.client")
@Data
@ComponentScan
public class ApiClientConfig {

    private String accessKey;
    private String secretKey;

    /**
     * 将 ApiClient 注入到 Spring 容器中
     */
    @Bean
    public ApiClient apiClient() {
        // 使用配置文件中读取到的 ak 和 sk 实例化我们的客户端
        return new ApiClient(accessKey, secretKey);
    }
}