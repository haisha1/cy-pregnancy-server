package com.chunyu.pregnancy.config;

import com.chunyu.pregnancy.common.result.ApiResponse;
import com.chunyu.pregnancy.security.SessionValidityFilter;
import com.chunyu.pregnancy.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 接口使用Authorization头中的Bearer token，不使用Cookie登录，因此禁用CSRF和HTTP Session。
 * 不开放全局跨域；H5联调需要时添加明确来源白名单。小程序不需要浏览器CORS放行。
 */
@Configuration
public class SecurityConfig {
  @Bean
  public SecretKey jwtKey(AuthProperties properties) {
    return new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  @Bean
  public JwtEncoder jwtEncoder(SecretKey key) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  public JwtDecoder jwtDecoder(SecretKey key, AuthProperties properties) {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    OAuth2TokenValidator<Jwt> claims =
        jwt -> {
          boolean valid =
              jwt.getAudience().contains("miniapp")
                  && "access".equals(jwt.getClaimAsString("kind"))
                  && jwt.getSubject() != null
                  && jwt.getSubject().matches("[1-9][0-9]*")
                  && jwt.getClaimAsString("sid") != null
                  && jwt.getExpiresAt() != null;
          return valid
              ? OAuth2TokenValidatorResult.success()
              : OAuth2TokenValidatorResult.failure(
                  new OAuth2Error("invalid_token", "登录凭据无效", null));
        };
    decoder.setJwtValidator(
        new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.getIssuer()), claims));
    return decoder;
  }

  @Bean
  public SecurityFilterChain apiSecurity(
      HttpSecurity http, ObjectMapper json, SessionService sessions, Environment environment)
      throws Exception {
    http.csrf().disable().formLogin().disable().httpBasic().disable().logout().disable();
    http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    http.authorizeRequests()
        .antMatchers(
            org.springframework.http.HttpMethod.GET, "/api/v1/knowledge", "/api/v1/knowledge/*")
        .permitAll()
        .antMatchers(
            org.springframework.http.HttpMethod.GET,
            "/internal/admin/users",
            "/internal/admin/users/**",
            "/internal/admin/user-stats")
        .permitAll()
        .antMatchers("/api/v1/capabilities", "/actuator/health")
        .permitAll()
        .antMatchers(
            org.springframework.http.HttpMethod.POST,
            "/api/v1/auth/wx-login",
            "/api/v1/auth/refresh")
        .permitAll();
    // 文档仅在local开放；生产关闭页面及JSON，避免部署时意外暴露文档调试入口。
    if (environment.acceptsProfiles(Profiles.of("local"))) {
      http.authorizeRequests()
          .antMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
          .permitAll();
    }
    http.authorizeRequests().anyRequest().authenticated();
    org.springframework.security.web.AuthenticationEntryPoint unauthorized =
        (request, response, ex) -> {
          response.setStatus(401);
          response.setContentType("application/json;charset=UTF-8");
          json.writeValue(response.getOutputStream(), ApiResponse.error("AUTH_EXPIRED", "请登录后访问"));
        };
    http.exceptionHandling()
        .authenticationEntryPoint(unauthorized)
        .accessDeniedHandler(
            (request, response, ex) -> {
              response.setStatus(403);
              response.setContentType("application/json;charset=UTF-8");
              json.writeValue(response.getOutputStream(), ApiResponse.error("FORBIDDEN", "没有访问权限"));
            });
    http.oauth2ResourceServer().authenticationEntryPoint(unauthorized).jwt();
    http.addFilterAfter(
        new SessionValidityFilter(sessions, json), BearerTokenAuthenticationFilter.class);
    return http.build();
  }
}
