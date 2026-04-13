package com.accycx.apiclientsdk.client;


import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import org.springframework.context.annotation.Configuration;

/**
 * ApiClient 自动配置类
 */
@Configuration
@EnableConfigurationProperties(ApiClientProperties.class) // 激活属性类
public class ApiClientConfig {

    @Bean
    public ApiClient apiClient(ApiClientProperties properties) {
        // 通过入参拿到已经装载好 YAML 数据的 properties
        return new ApiClient(properties.getAccessKey(), properties.getSecretKey());
    }
}