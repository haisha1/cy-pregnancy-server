package com.chunyu.pregnancy.controller;

import com.chunyu.pregnancy.service.AuthService;
import com.chunyu.pregnancy.common.ratelimit.LoginRateLimiter;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import com.chunyu.pregnancy.dto.request.*;
import com.chunyu.pregnancy.dto.response.*;
import com.chunyu.pregnancy.common.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/** 控制器只做参数接收和响应包装；身份校验在过滤器，业务事务在AuthService。 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "小程序账号", description = "微信登录不代表同意云同步")
public class AuthController {
    private final AuthService auth;
    private final LoginRateLimiter rateLimiter;
    public AuthController(AuthService auth, LoginRateLimiter rateLimiter) {
        this.auth = auth;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/auth/wx-login")
    @Operation(summary = "使用微信一次性code登录，不接收客户端openid")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        rateLimiter.check("login", request.getRemoteAddr());
        return ApiResponse.ok(auth.login(body));
    }

    @PostMapping("/auth/refresh")
    @Operation(summary = "轮换续期凭据；客户端必须串行刷新，不能重放旧refreshToken")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest body, HttpServletRequest request) {
        rateLimiter.check("refresh", request.getRemoteAddr());
        return ApiResponse.ok(auth.refresh(body.refreshToken));
    }

    @PostMapping("/auth/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "撤销当前会话，立即阻断尚未到期的JWT")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        auth.logout(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("sid"));
        return ApiResponse.ok(null);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "读取当前账户最小信息")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(auth.me(Long.valueOf(jwt.getSubject())));
    }
}
