package com.accycx.apiclientsdk.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API 客户端配置属性（纯数据类）
 */
@Data
//这个注解的意思是：去 application.yml 里读取前缀为 "api.client" 的配置，映射到这个类的属性上
@ConfigurationProperties("api.client")
public class ApiClientProperties {

    /**
     * 访问密钥（只要加上这行注释，别人敲 YAML 时就会有中文提示）
     */
    private String accessKey;

    /**
     * 秘密密钥
     */
    private String secretKey;
}