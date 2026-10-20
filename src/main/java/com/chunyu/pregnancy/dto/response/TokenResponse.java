package com.chunyu.pregnancy.dto.response;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 登录/续期结果，仅在签发时返回凭据，不返回数据库实体及其内部字段。 */
@Getter
@AllArgsConstructor
public class TokenResponse {
    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final long expiresIn;
    private final Instant refreshExpiresAt;
}
