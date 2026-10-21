package com.chunyu.pregnancy.controller;

import com.chunyu.pregnancy.common.result.ApiResponse;
import com.chunyu.pregnancy.service.PublishedContentService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** 游客可浏览知识，不读取账号或健康档案；no-store保证刷新时重新确认发布状态。 */
// HTTP 控制器：接收请求，返回值由框架序列化为响应，通常是 JSON。
@RestController
// 声明接口路径；类上的公共前缀会与方法上的路径拼接。
@RequestMapping("/api/v1/knowledge")
public class PublishedContentController {
  private final PublishedContentService content;

  // 单一构造器由 Spring 自动注入依赖；final 字段表明这些依赖在构造后不再替换。
  public PublishedContentController(PublishedContentService content) {
    this.content = content;
  }

  // 将下面的方法映射为 GET 查询接口，不在此声明中处理数据库事务。
  @GetMapping
  public ResponseEntity<ApiResponse<Object>> list(
      // 读取查询参数或表单参数，默认值和必填要求以注解参数为准。
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
