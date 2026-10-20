package com.chunyu.pregnancy.controller;

import com.chunyu.pregnancy.common.exception.BizException;
import com.chunyu.pregnancy.common.result.ApiResponse;
import com.chunyu.pregnancy.service.AdminUserReadService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/** 服务间最小只读边界。不是浏览器API，不接受管理员JWT代替内部密钥；未配置时拒绝。 */
@RestController
@RequestMapping("/internal/admin")
public class AdminUserReadController {
  private final AdminUserReadService service;

  @Value("${ADMIN_READ_KEY:}")
  private String key;

  public AdminUserReadController(AdminUserReadService service) {
    this.service = service;
  }

  private void require(String supplied) {
    if (key.length() < 32) throw new BizException(503, "INTERNAL_UNAVAILABLE", "内部查询未配置");
    if (supplied == null
        || supplied.length() > 256
        || !MessageDigest.isEqual(
            key.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8)))
      throw new BizException(403, "FORBIDDEN", "内部查询鉴权失败");
  }

  @GetMapping("/users")
  public ApiResponse<Map<String, Object>> list(
      @RequestHeader(value = "X-Admin-Read-Key", required = false) String supplied,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) Long beforeId,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String start,
      @RequestParam(required = false) String end) {
    require(supplied);
    return ApiResponse.ok(service.list(q, state, beforeId, limit, start, end));
  }

  @GetMapping("/users/{uuid}")
  public ApiResponse<Map<String, Object>> detail(
      @RequestHeader(value = "X-Admin-Read-Key", required = false) String supplied,
      @PathVariable String uuid) {
    require(supplied);
    return ApiResponse.ok(service.detail(uuid));
  }

  @GetMapping("/user-stats")
  public ApiResponse<Map<String, Object>> stats(
      @RequestHeader(value = "X-Admin-Read-Key", required = false) String supplied,
      @RequestParam(defaultValue = "7") int days) {
    require(supplied);
    return ApiResponse.ok(service.stats(days));
  }
}
