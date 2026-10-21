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
// HTTP 控制器：接收请求，返回值由框架序列化为响应，通常是 JSON。
@RestController
// 声明接口路径；类上的公共前缀会与方法上的路径拼接。
@RequestMapping("/api/v1")
// OpenAPI 文档分组说明，仅影响接口文档展示，不负责真实鉴权。
@Tag(name = "小程序账号", description = "微信登录不代表同意云同步")
public class AuthController {
    private final AuthService auth;
    private final LoginRateLimiter rateLimiter;
    // 单一构造器由 Spring 自动注入依赖；final 字段表明这些依赖在构造后不再替换。
    public AuthController(AuthService auth, LoginRateLimiter rateLimiter) {
        this.auth = auth;
        this.rateLimiter = rateLimiter;
    }

    // 将下面的方法映射为 POST 接口；请求体解析和业务处理由参数及方法体决定。
    // 参数中的 RequestBody 将 JSON 转成登录 DTO，Valid 再执行字段约束；通过后才进入方法体。
    @PostMapping("/auth/wx-login")
    // OpenAPI 接口说明，仅描述用途；参数校验和鉴权仍由其他注解及代码执行。
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
    // 声明 OpenAPI 文档中的认证要求，不会代替 Spring Security 的实际拦截。
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "撤销当前会话，立即阻断尚未到期的JWT")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        auth.logout(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("sid"));
        return ApiResponse.ok(null);
    }

    // 将下面的方法映射为 GET 查询接口，不在此声明中处理数据库事务。
    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "读取当前账户最小信息")
    public ApiResponse<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(auth.me(Long.valueOf(jwt.getSubject())));
    }
}
