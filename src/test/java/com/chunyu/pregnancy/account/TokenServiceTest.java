package com.chunyu.pregnancy.account;

import com.chunyu.pregnancy.security.TokenService;
import com.chunyu.pregnancy.common.util.DigestUtils;

import com.chunyu.pregnancy.config.AuthProperties;
import com.chunyu.pregnancy.config.SecurityConfig;
import java.time.Instant;
import java.util.Collections;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.*;

/** 不依赖数据库的令牌契约测试，验证用途、接收方和签名隔离。 */
class TokenServiceTest {
    private final AuthProperties properties = properties();
    private final SecurityConfig config = new SecurityConfig();
    private final SecretKey key = config.jwtKey(properties);
    private final JwtEncoder encoder = config.jwtEncoder(key);
    private final JwtDecoder decoder = config.jwtDecoder(key, properties);
    private final TokenService service = new TokenService(encoder, properties);

    private static AuthProperties properties() {
        AuthProperties result = new AuthProperties();
        result.setSecret("unit-test-only-key-with-at-least-32-bytes");
        return result;
    }

    @Test void validAccessTokenContainsOnlyMinimalIdentity() {
        Jwt jwt = decoder.decode(service.accessToken(123L, "session-123"));
        assertThat(jwt.getSubject()).isEqualTo("123");
        assertThat(jwt.getClaimAsString("sid")).isEqualTo("session-123");
        assertThat(jwt.getClaims()).doesNotContainKeys("openid", "session_key", "profile", "refreshToken");
    }

    @Test void wrongAudienceIsRejectedEvenWithValidSignature() {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(properties.getIssuer()).subject("123")
                .audience(Collections.singletonList("admin")).issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60)).claim("sid", "s").claim("kind", "access").build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test void wrongKeyCannotVerifySignature() {
        AuthProperties other = properties();
        other.setSecret("a-different-unit-test-key-with-32-bytes");
        JwtDecoder otherDecoder = config.jwtDecoder(config.jwtKey(other), other);
        assertThatThrownBy(() -> otherDecoder.decode(service.accessToken(1L, "s"))).isInstanceOf(JwtException.class);
    }

    @Test void refreshTokensAreRandomAndOnlyDigestIsStored() {
        String first = service.randomRefreshToken();
        String second = service.randomRefreshToken();
        assertThat(first).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(second);
        assertThat(DigestUtils.sha256(first)).hasSize(64).isNotEqualTo(first);
    }
}
