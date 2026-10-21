package com.chunyu.pregnancy.security;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import com.chunyu.pregnancy.config.AuthProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

/** access token为短期JWT；refresh token为随机字符串，数据库只存SHA-256散列。 */
// 业务层组件：由 Spring 管理，负责组织业务步骤；事务边界仍需由事务代码明确控制。
@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final AuthProperties properties;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JwtEncoder encoder, AuthProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    // JWT 携带用户、受众、会话和到期时间；签名保证内容未被篡改，不表示会话永远有效。
    public String accessToken(Long userId, String sessionUuid) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.getIssuer())
                .subject(userId.toString()).audience(Collections.singletonList("miniapp"))
                .issuedAt(now).expiresAt(now.plusSeconds(properties.getAccessSeconds()))
                .claim("sid", sessionUuid).claim("kind", "access").build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    // 用安全随机数生成不可预测的刷新凭据；URL-safe Base64 便于传输，不是对密码做加密。
    public String randomRefreshToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}
