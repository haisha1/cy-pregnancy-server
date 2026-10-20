package com.chunyu.pregnancy.service;

import com.chunyu.pregnancy.common.exception.BizException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;
import org.springframework.web.util.UriComponentsBuilder;

/** 用户服务的内容只读边界。独立内部密钥、短超时、无历史正文缓存，下架时不能回退显示旧发布版本。 */
@Service
public class PublishedContentService {
  private final RestTemplate http;
  private final String base, key;

  public PublishedContentService(
      RestTemplateBuilder builder,
      @Value("${CONTENT_READ_URL:http://127.0.0.1:18732}") String base,
      @Value("${CONTENT_READ_KEY:}") String key) {
    http =
        builder
            .setConnectTimeout(java.time.Duration.ofMillis(1500))
            .setReadTimeout(java.time.Duration.ofMillis(2500))
            .build();
    this.base = base.replaceAll("/+$", "");
    this.key = key;
  }

  public Object read(String uuid, String q, String category, Long before, int limit) {
    if (key.length() < 32) throw new BizException(503, "CONTENT_NOT_CONFIGURED", "发布内容服务尚未配置");
    if (limit < 1
        || limit > 100
        || before != null && before < 1
        || q != null && q.length() > 100
        || category != null && category.length() > 64)
      throw new BizException(422, "QUERY_INVALID", "查询条件无效");
    if (uuid != null && !uuid.matches("[0-9a-fA-F-]{36}"))
      throw new BizException(404, "CONTENT_NOT_FOUND", "内容不存在或已下架");
    UriComponentsBuilder uri =
        UriComponentsBuilder.fromHttpUrl(
            base + "/internal/content/knowledge" + (uuid == null ? "" : "/" + uuid));
    if (uuid == null) {
      uri.queryParam("limit", limit);
      if (q != null) uri.queryParam("q", q);
      if (category != null) uri.queryParam("category", category);
      if (before != null) uri.queryParam("beforeId", before);
    }
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Content-Read-Key", key);
    try {
      Map<?, ?> body =
          http.exchange(
                  uri.build().encode().toUri(),
                  HttpMethod.GET,
                  new HttpEntity<>(headers),
                  Map.class)
              .getBody();
      if (body == null || !"OK".equals(body.get("code")) || body.get("data") == null)
        throw unavailable();
      return body.get("data");
    } catch (HttpClientErrorException ex) {
      if (ex.getRawStatusCode() == 404)
        throw new BizException(404, "CONTENT_NOT_FOUND", "内容不存在或已下架");
      throw unavailable();
    } catch (RestClientException ex) {
      throw unavailable();
    }
  }

  private BizException unavailable() {
    return new BizException(503, "CONTENT_UNAVAILABLE", "发布内容暂不可用，请稍后重试");
  }
}
