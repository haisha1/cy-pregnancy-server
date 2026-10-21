package com.chunyu.pregnancy.security;

import java.io.IOException;
import javax.servlet.*;
import javax.servlet.http.*;
import com.chunyu.pregnancy.service.SessionService;
import com.chunyu.pregnancy.common.result.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

/** JWT验签之后再查权威会话，确保退出、封禁立即生效，不依赖等待JWT自然到期。 */
public class SessionValidityFilter extends OncePerRequestFilter {
    private final SessionService sessions;
    private final ObjectMapper json;
    public SessionValidityFilter(SessionService sessions, ObjectMapper json) {
        this.sessions = sessions;
        this.json = json;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // SecurityContextHolder 保存当前请求线程的认证信息；这里读取上游 JWT 验签后的结果。
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken) {
            Jwt jwt = ((JwtAuthenticationToken) authentication).getToken();
            boolean active;
            try {
                active = sessions.isActive(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("sid"));
            } catch (DataAccessException ex) {
                writeError(response, 503, "AUTH_SERVICE_UNAVAILABLE", "会话校验暂不可用");
                return;
            } catch (IllegalArgumentException ex) {
                active = false;
            }
            if (!active) {
                SecurityContextHolder.clearContext();
                writeError(response, 401, "AUTH_EXPIRED", "会话无效，请重新登录");
                return;
            }
        }
        // 只有校验未提前返回时才把请求交给后续过滤器，最终进入 Controller。
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getOutputStream(), ApiResponse.error(code, message));
    }
}
