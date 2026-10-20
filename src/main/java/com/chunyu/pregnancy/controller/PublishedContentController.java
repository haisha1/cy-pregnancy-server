package com.chunyu.pregnancy.controller;

import com.chunyu.pregnancy.common.result.ApiResponse;
import com.chunyu.pregnancy.service.PublishedContentService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 游客可浏览知识，不读取账号或健康档案；no-store保证刷新时重新确认发布状态。 */
@RestController
@RequestMapping("/api/v1/knowledge")
public class PublishedContentController {
  private final PublishedContentService content;

  public PublishedContentController(PublishedContentService content) {
    this.content = content;
  }

  @GetMapping
  public ResponseEntity<ApiResponse<Object>> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String category,
      @RequestParam(required = false) Long beforeId,
      @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.ok(content.read(null, q, category, beforeId, limit)));
  }

  @GetMapping("/{uuid}")
  public ResponseEntity<ApiResponse<Object>> detail(@PathVariable String uuid) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(ApiResponse.ok(content.read(uuid, null, null, null, 20)));
  }
}
