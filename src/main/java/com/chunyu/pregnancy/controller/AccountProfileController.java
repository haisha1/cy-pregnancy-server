package com.chunyu.pregnancy.controller;

import com.chunyu.pregnancy.common.ratelimit.LoginRateLimiter;
import com.chunyu.pregnancy.common.result.ApiResponse;
import com.chunyu.pregnancy.dto.response.MeResponse;
import com.chunyu.pregnancy.service.AccountProfileService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 从JWT确定当前用户，客户端不能指定目标账号。图片下载要求Bearer，不使用带token的URL。 */
@RestController
@RequestMapping("/api/v1/me")
public class AccountProfileController {
  private final AccountProfileService profiles;
  private final LoginRateLimiter limiter;

  public AccountProfileController(AccountProfileService profiles, LoginRateLimiter limiter) {
    this.profiles = profiles;
    this.limiter = limiter;
  }

  @PostMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<MeResponse> update(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam String nickname,
      @RequestParam String wxCode,
      @RequestParam long revision,
      @RequestPart(required = false) MultipartFile avatar)
      throws java.io.IOException {
    limiter.check("profile", jwt.getSubject());
    return ApiResponse.ok(
        profiles.update(
            Long.valueOf(jwt.getSubject()),
            nickname,
            wxCode,
            revision,
            avatar == null ? null : avatar.getBytes()));
  }
  /** 没有新头像时支持JSON，uni.request无需制造一个空上传文件。 */
  @PostMapping(value = "/profile", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ApiResponse<MeResponse> updateText(
      @AuthenticationPrincipal Jwt jwt, @javax.validation.Valid @RequestBody ProfileRequest body) {
    limiter.check("profile", jwt.getSubject());
    return ApiResponse.ok(
        profiles.update(
            Long.valueOf(jwt.getSubject()), body.nickname, body.wxCode, body.revision, null));
  }

  @DeleteMapping("/profile")
  public ApiResponse<MeResponse> clear(
      @AuthenticationPrincipal Jwt jwt, @RequestParam long revision) {
    return ApiResponse.ok(profiles.clear(Long.valueOf(jwt.getSubject()), revision));
  }

  @GetMapping("/avatar")
  public ResponseEntity<byte[]> avatar(@AuthenticationPrincipal Jwt jwt) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .cacheControl(CacheControl.noStore())
        .header("X-Content-Type-Options", "nosniff")
        .body(profiles.avatar(Long.valueOf(jwt.getSubject())));
  }

  public static class ProfileRequest {
    @javax.validation.constraints.NotNull
    @javax.validation.constraints.Size(max = 32)
    public String nickname;

    @javax.validation.constraints.NotBlank
    @javax.validation.constraints.Size(max = 256)
    public String wxCode;

    @javax.validation.constraints.NotNull
    @javax.validation.constraints.Min(0)
    public Long revision;
  }
}
