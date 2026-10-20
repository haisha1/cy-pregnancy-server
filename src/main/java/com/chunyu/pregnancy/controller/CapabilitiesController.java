package com.chunyu.pregnancy.controller;

import com.chunyu.pregnancy.common.result.ApiResponse;
import com.chunyu.pregnancy.config.WechatProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 如实声明能力；后续模块通过验收再打开，前端不能据此跳过服务端权限校验。 */
@RestController
public class CapabilitiesController {
  private final WechatProperties wechat;

  @org.springframework.beans.factory.annotation.Value("${CONTENT_READ_KEY:}")
  private String contentKey;

  public CapabilitiesController(WechatProperties wechat) {
    this.wechat = wechat;
  }

  @GetMapping("/api/v1/capabilities")
  public ApiResponse<Map<String, Boolean>> capabilities() {
    Map<String, Boolean> result = new LinkedHashMap<>();
    result.put("auth", wechat.isConfigured());
    result.put("accountProfile", wechat.isConfigured());
    result.put("knowledgeRead", contentKey.length() >= 32);
    for (String key :
        new String[] {
          "sync",
          "preferencesSync",
          "ocr",
          "feedback",
          "cloudShare",
          "reminders",
          "cloudExport",
          "cloudDeletion"
        }) {
      result.put(key, false);
    }
    return ApiResponse.ok(result);
  }
}
