package com.chunyu.pregnancy.account;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.chunyu.pregnancy.common.exception.BizException;
import com.chunyu.pregnancy.common.ratelimit.LoginRateLimiter;
import com.chunyu.pregnancy.integration.wechat.WechatGateway;
import com.chunyu.pregnancy.security.TokenService;
import com.chunyu.pregnancy.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 真实MySQL/Flyway/Security集成测试。只允许连接独立测试库，绝不清理开发或生产库。 微信边界用测试替身，避免依赖真实code；该替身不会打包到生产jar。
 * 执行前创建cy_pregnancy_auth_test，并设置RUN_MYSQL_IT=true。
 */
@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_IT", matches = "true")
@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:mysql://localhost:3306/cy_pregnancy_auth_test?serverTimezone=UTC",
      "spring.datasource.username=root",
      "spring.datasource.password=root",
      "app.jwt.secret=integration-test-only-secret-with-at-least-32-bytes",
      "app.wechat.app-id=test-app-id",
      "app.wechat.secret=test-secret",
      "ADMIN_READ_KEY=integration-internal-read-only-secret-32-bytes"
    })
@AutoConfigureMockMvc
class AuthMysqlIntegrationTest {
  @Test
  void optionalProfileRoundTripIsPrivateVersionedAndClearable() throws Exception {
    String bearer = "Bearer " + data(login()).get("accessToken");
    mvc.perform(get("/api/v1/me").header("Authorization", bearer))
        .andExpect(jsonPath("$.data.nickname").isEmpty())
        .andExpect(jsonPath("$.data.hasAvatar").value(false));
    java.awt.image.BufferedImage image =
        new java.awt.image.BufferedImage(400, 400, java.awt.image.BufferedImage.TYPE_INT_RGB);
    java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", bytes);
    mvc.perform(
            multipart("/api/v1/me/profile")
                .file(
                    new org.springframework.mock.web.MockMultipartFile(
                        "avatar", "avatar.png", "image/png", bytes.toByteArray()))
                .param("nickname", "春雨用户")
                .param("wxCode", "fresh-code")
                .param("revision", "0")
                .header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.nickname").value("春雨用户"))
        .andExpect(jsonPath("$.data.hasAvatar").value(true))
        .andExpect(jsonPath("$.data.profileRevision").value(1));
    org.mockito.Mockito.verify(wechat).checkNickname("test-openid-not-a-real-account", "春雨用户");
    mvc.perform(get("/api/v1/me/avatar")).andExpect(status().isUnauthorized());
    byte[] jpeg =
        mvc.perform(get("/api/v1/me/avatar").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();
    assertThat(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(jpeg)).getWidth())
        .isEqualTo(256);
    mvc.perform(
            post("/api/v1/me/profile")
                .contentType("application/json")
                .header("Authorization", bearer)
                .content("{\"nickname\":\"conflict\",\"wxCode\":\"fresh\",\"revision\":0}"))
        .andExpect(status().isConflict());
    mvc.perform(delete("/api/v1/me/profile").param("revision", "1").header("Authorization", bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.hasAvatar").value(false))
        .andExpect(jsonPath("$.data.nickname").isEmpty())
        .andExpect(jsonPath("$.data.profileRevision").value(2));
    mvc.perform(get("/api/v1/me/avatar").header("Authorization", bearer))
        .andExpect(status().isNotFound());
  }

  @Test
  void failedModerationNeverPersistsAndWechatIdentityMustMatch() throws Exception {
    String bearer = "Bearer " + data(login()).get("accessToken");
    org.mockito.Mockito.doThrow(new BizException(503, "MODERATION_UNAVAILABLE", "检查不可用"))
        .when(wechat)
        .checkNickname(anyString(), anyString());
    String body = "{\"nickname\":\"test\",\"wxCode\":\"fresh\",\"revision\":0}";
    mvc.perform(
            post("/api/v1/me/profile")
                .contentType("application/json")
                .header("Authorization", bearer)
                .content(body))
        .andExpect(status().isServiceUnavailable());
    assertThat(jdbc.queryForObject("SELECT profile_revision FROM app_user", Long.class)).isZero();
    when(wechat.exchangeOpenId("other-code")).thenReturn("another-wechat-account");
    mvc.perform(
            post("/api/v1/me/profile")
                .contentType("application/json")
                .header("Authorization", bearer)
                .content(body.replace("fresh", "other-code")))
        .andExpect(status().isForbidden());
    assertThat(jdbc.queryForObject("SELECT nickname FROM app_user", String.class)).isNull();
  }

  @Test
  void avatarPayloadAndNicknameAreValidatedWithoutSaving() throws Exception {
    String bearer = "Bearer " + data(login()).get("accessToken");
    mvc.perform(
            multipart("/api/v1/me/profile")
                .file(
                    new org.springframework.mock.web.MockMultipartFile(
                        "avatar",
                        "fake.jpg",
                        "image/jpeg",
                        "not-image".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .param("nickname", "")
                .param("wxCode", "fresh-code")
                .param("revision", "0")
                .header("Authorization", bearer))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            post("/api/v1/me/profile")
                .contentType("application/json")
                .header("Authorization", bearer)
                .content("{\"nickname\":\"bad\\nname\",\"wxCode\":\"fresh\",\"revision\":0}"))
        .andExpect(status().isUnprocessableEntity());
    assertThat(jdbc.queryForObject("SELECT profile_revision FROM app_user", Long.class)).isZero();
  }

  @Test
  void internalAdminReadIsGatedBoundedAndDoesNotLeakIdentity() throws Exception {
    login();
    mvc.perform(get("/internal/admin/users")).andExpect(status().isForbidden());
    mvc.perform(get("/internal/admin/users").header("X-Admin-Read-Key", "wrong"))
        .andExpect(status().isForbidden());
    String key = "integration-internal-read-only-secret-32-bytes";
    String body =
        mvc.perform(get("/internal/admin/users").header("X-Admin-Read-Key", key))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records[0].uuid").isString())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body)
        .doesNotContain("subject_hash", "subjectHash", "refreshToken", "installationId", "appId");
    String uuid = json.readTree(body).at("/data/records/0/uuid").asText();
    mvc.perform(get("/internal/admin/users").header("X-Admin-Read-Key", key).param("q", "中文%_!"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.records.length()").value(0));
    assertThat(UUID.fromString(uuid)).isNotNull();
    mvc.perform(get("/internal/admin/users/" + uuid).header("X-Admin-Read-Key", key))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.activeSessions").value(1));
    mvc.perform(get("/internal/admin/users").header("X-Admin-Read-Key", key).param("limit", "101"))
        .andExpect(status().isUnprocessableEntity());
    mvc.perform(
            get("/internal/admin/user-stats").header("X-Admin-Read-Key", key).param("days", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.total").value(1))
        .andExpect(jsonPath("$.data.trend.length()").value(7));
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired AuthService auth;
  @Autowired TokenService tokens;
  @Autowired LoginRateLimiter limiter;
  @MockBean WechatGateway wechat;

  @BeforeEach
  void resetOwnedTestDatabase() {
    assertThat(jdbc.queryForObject("SELECT DATABASE()", String.class))
        .isEqualTo("cy_pregnancy_auth_test");
    jdbc.update("DELETE FROM refresh_token_history");
    jdbc.update("DELETE FROM auth_session");
    jdbc.update("DELETE FROM app_user");
    when(wechat.exchangeOpenId(anyString())).thenReturn("test-openid-not-a-real-account");
  }

  private Map<String, Object> login() throws Exception {
    String response =
        mvc.perform(
                post("/api/v1/auth/wx-login")
                    .contentType("application/json")
                    .content(
                        "{\"code\":\"test-code\",\"installationId\":\"test-installation-001\"}"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readValue(
        response, json.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> data(Map<String, Object> response) {
    return (Map<String, Object>) response.get("data");
  }

  private String refreshBody(String refresh) throws Exception {
    return json.writeValueAsString(java.util.Collections.singletonMap("refreshToken", refresh));
  }

  @Test
  void anonymousAccessIsRejectedAndCapabilitiesDoNotPretendSyncWorks() throws Exception {
    mvc.perform(get("/api/v1/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.requestId").isNotEmpty());
    mvc.perform(get("/api/v1/capabilities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.sync").value(false));
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isZero();
  }

  @Test
  void repeatedWechatIdentityCreatesOneUserAndNoSensitiveResponseFields() throws Exception {
    Map<String, Object> first = data(login());
    login();
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_session", Integer.class))
        .isEqualTo(2);
    assertThat(first).doesNotContainKeys("openid", "session_key", "subjectHash");
    assertThat(jdbc.queryForList("SELECT refresh_token_hash FROM auth_session", String.class))
        .doesNotContain((String) first.get("refreshToken"));
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + first.get("accessToken")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.cloudSyncEnabled").value(false));
  }

  @Test
  void logoutImmediatelyInvalidatesAccessAndRefresh() throws Exception {
    Map<String, Object> current = data(login());
    String bearer = "Bearer " + current.get("accessToken");
    mvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/me").header("Authorization", bearer))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType("application/json")
                .content(refreshBody((String) current.get("refreshToken"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshRotatesAndOldRefreshReplayRevokesNewAccess() throws Exception {
    Map<String, Object> original = data(login());
    String result =
        mvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType("application/json")
                    .content(refreshBody((String) original.get("refreshToken"))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    Map<String, Object> rotated = data(json.readValue(result, Map.class));
    assertThat(rotated.get("refreshToken")).isNotEqualTo(original.get("refreshToken"));
    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType("application/json")
                .content(refreshBody((String) original.get("refreshToken"))))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + rotated.get("accessToken")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void sessionMustBelongToJwtUserEvenWithValidSignature() throws Exception {
    data(login());
    Long owner = jdbc.queryForObject("SELECT user_id FROM auth_session LIMIT 1", Long.class);
    String session =
        jdbc.queryForObject("SELECT client_uuid FROM auth_session LIMIT 1", String.class);
    String forged = tokens.accessToken(owner + 100L, session);
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + forged))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredDatabaseSessionInvalidatesUnexpiredJwt() throws Exception {
    Map<String, Object> current = data(login());
    jdbc.update("UPDATE auth_session SET expires_at=DATE_SUB(UTC_TIMESTAMP(),INTERVAL 1 DAY)");
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + current.get("accessToken")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void blockedAccountCannotContinueUsingSession() throws Exception {
    Map<String, Object> current = data(login());
    jdbc.update("UPDATE app_user SET account_status='closed'");
    mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + current.get("accessToken")))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            post("/api/v1/auth/refresh")
                .contentType("application/json")
                .content(refreshBody((String) current.get("refreshToken"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void invalidWechatCodeDoesNotCreateUser() throws Exception {
    when(wechat.exchangeOpenId(anyString()))
        .thenThrow(new BizException(401, "WX_CODE_INVALID", "登录凭证无效"));
    mvc.perform(
            post("/api/v1/auth/wx-login")
                .contentType("application/json")
                .content("{\"code\":\"bad\",\"installationId\":\"test-installation-001\"}"))
        .andExpect(status().isUnauthorized());
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isZero();
  }

  @Test
  void clientCannotSupplyOpenidOrOtherUnexpectedFields() throws Exception {
    mvc.perform(
            post("/api/v1/auth/wx-login")
                .contentType("application/json")
                .content(
                    "{\"code\":\"code\",\"installationId\":\"test-installation-001\",\"openid\":\"forged\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void redisLimiterCountsAtomicallyAndRejectsThirtyFirstRequest() {
    String testAddress = "integration-" + UUID.randomUUID();
    for (int i = 0; i < 30; i++) {
      limiter.check("test", testAddress);
    }
    assertThatThrownBy(() -> limiter.check("test", testAddress))
        .isInstanceOf(BizException.class)
        .satisfies(ex -> assertThat(((BizException) ex).getStatus()).isEqualTo(429));
  }

  @Test
  void concurrentRefreshAllowsOnlyOneRotationAndDetectsReplay() throws Exception {
    Map<String, Object> current = data(login());
    String refresh = (String) current.get("refreshToken");
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    Callable<Boolean> attempt =
        () -> {
          start.await();
          try {
            auth.refresh(refresh);
            return true;
          } catch (BizException ex) {
            assertThat(ex.getStatus()).isEqualTo(401);
            return false;
          }
        };
    try {
      Future<Boolean> first = pool.submit(attempt);
      Future<Boolean> second = pool.submit(attempt);
      start.countDown();
      assertThat(first.get(10, TimeUnit.SECONDS) ^ second.get(10, TimeUnit.SECONDS)).isTrue();
      assertThat(
              jdbc.queryForObject(
                  "SELECT COUNT(*) FROM auth_session WHERE revoked_at IS NULL", Integer.class))
          .isZero();
    } finally {
      pool.shutdownNow();
    }
  }
}
