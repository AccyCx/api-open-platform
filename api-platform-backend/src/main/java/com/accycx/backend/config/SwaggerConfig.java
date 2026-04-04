package com.accycx.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("API 开放平台接口文档")
                        .version("1.0")
                        .description("基于 Spring Boot 3 + Vue3 的企业级 API 开放平台"));
    }
}