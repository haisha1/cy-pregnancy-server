package com.chunyu.pregnancy.account;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.chunyu.pregnancy.common.exception.BizException;
import com.chunyu.pregnancy.service.PublishedContentService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class PublishedContentTest {
  @Test
  void proxyUsesIndependentKeyAndNeverCachesWithdrawnContent() {
    PublishedContentService service =
        new PublishedContentService(
            new RestTemplateBuilder(),
            "http://content.test",
            "integration-content-only-secret-32-bytes");
    MockRestServiceServer server =
        MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "http"))
            .build();
    String uuid = "11111111-1111-4111-8111-111111111111";
    server
        .expect(requestTo("http://content.test/internal/content/knowledge/" + uuid))
        .andExpect(header("X-Content-Read-Key", "integration-content-only-secret-32-bytes"))
        .andRespond(
            withSuccess(
                "{\"code\":\"OK\",\"data\":{\"title\":\"published\"}}",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("http://content.test/internal/content/knowledge/" + uuid))
        .andRespond(withStatus(HttpStatus.NOT_FOUND));
    assertThat(service.read(uuid, null, null, null, 20)).isNotNull();
    assertThatThrownBy(() -> service.read(uuid, null, null, null, 20))
        .isInstanceOfSatisfying(
            BizException.class, ex -> assertThat(ex.getStatus()).isEqualTo(404));
    server.verify();
  }

  @Test
  void invalidQueriesFailBeforeNetworkAndMissingConfigIsExplicit() {
    PublishedContentService missing =
        new PublishedContentService(new RestTemplateBuilder(), "http://content.test", "");
    assertThatThrownBy(() -> missing.read(null, null, null, null, 20))
        .isInstanceOfSatisfying(
            BizException.class, ex -> assertThat(ex.getStatus()).isEqualTo(503));
    PublishedContentService service =
        new PublishedContentService(
            new RestTemplateBuilder(),
            "http://content.test",
            "integration-content-only-secret-32-bytes");
    assertThatThrownBy(() -> service.read(null, null, null, null, 101))
        .isInstanceOfSatisfying(
            BizException.class, ex -> assertThat(ex.getStatus()).isEqualTo(422));
  }
}
