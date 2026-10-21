package com.chunyu.pregnancy.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 文档只展示当前已实现的控制器；公开接口不要误标成需要Bearer。 */
// 配置类：集中声明需要交给 Spring 管理的对象及框架行为。
@Configuration
public class OpenApiConfig {
    // 把方法返回的对象注册为 Spring Bean；参数中的依赖由 Spring 从容器中提供。
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI().info(new Info().title("产科工具 · 小程序用户端API").version("0.1.0")
                .description("已实现账号基础模块。健康数据同步、OCR等尚未实现，以capabilities为准。"))
                .addServersItem(new Server().url("/").description("当前服务地址；Apifox导入后配置环境Base URL"))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
