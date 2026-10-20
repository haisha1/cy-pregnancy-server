package com.chunyu.pregnancy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 可在未配置微信时启动本地工程，但真实登录会返回503，绝不模拟微信身份。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.wechat")
public class WechatProperties {
    private String appId;
    private String secret;
    public boolean isConfigured() { return StringUtils.hasText(appId) && StringUtils.hasText(secret); }
}
