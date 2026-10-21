package com.chunyu.pregnancy.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 登录/续期结果，仅在签发时返回凭据，不返回数据库实体及其内部字段。 */
// Lombok 在编译期生成 getter；IDE 需要启用注解处理，运行时不靠反射生成方法。
@Getter
@AllArgsConstructor
public class TokenResponse {
    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long expiresIn;
    private final Instant refreshExpiresAt;
}
