package com.chunyu.pregnancy.config;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** 集中管理登录参数；没有密钥时启动失败，避免开发默认密钥被带到云端。 */
// Lombok 在编译期生成 getter；IDE 需要启用注解处理，运行时不靠反射生成方法。
@Getter
// Lombok 在编译期生成 setter，减少字段赋值方法的重复代码。
@Setter
// 启用此处的参数约束校验；配置类可校验配置值，Controller 可校验方法参数。
@Validated
// 通用 Spring 组件：由容器创建并管理，其他类可通过构造器使用它。
@Component
// 按 prefix 批量绑定外部配置，避免在业务代码里硬编码连接信息、密钥或有效期。
@ConfigurationProperties(prefix = "app.jwt")
public class AuthProperties {
    // 要求文本非 null 且不能全是空白字符，用于必填文本。
    @NotBlank @Size(min = 32)
    private String secret;
    @NotBlank
    private String issuer = "cy-pregnancy";
    // 限制数值下界，常用于限制有效期、页大小等参数。
    @Min(60)
    private long accessSeconds = 900;
    @Min(300)
    private long refreshSeconds = 2592000;
}
