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
@Getter
@Setter
@Validated
@Component
@ConfigurationProperties(prefix = "app.jwt")
public class AuthProperties {
    @NotBlank @Size(min = 32)
    private String secret;
    @NotBlank
    private String issuer = "cy-pregnancy";
    @Min(60)
    private long accessSeconds = 900;
    @Min(300)
    private long refreshSeconds = 2592000;
}
