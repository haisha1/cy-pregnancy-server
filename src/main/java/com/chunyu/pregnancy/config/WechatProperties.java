package com.chunyu.pregnancy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 可在未配置微信时启动本地工程，但真实登录会返回503，绝不模拟微信身份。 */
// Lombok 在编译期生成 getter；IDE 需要启用注解处理，运行时不靠反射生成方法。
@Getter
// Lombok 在编译期生成 setter，减少字段赋值方法的重复代码。
@Setter
// 通用 Spring 组件：由容器创建并管理，其他类可通过构造器使用它。
@Component
// 按 prefix 批量绑定外部配置，避免在业务代码里硬编码连接信息、密钥或有效期。
@ConfigurationProperties(prefix = "app.wechat")
public class WechatProperties {
    private String appId;
    private String secret;
    public boolean isConfigured() { return StringUtils.hasText(appId) && StringUtils.hasText(secret); }
}
