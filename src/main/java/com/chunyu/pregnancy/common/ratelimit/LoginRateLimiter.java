package com.chunyu.pregnancy.common.ratelimit;

import com.chunyu.pregnancy.common.util.DigestUtils;

import java.util.Collections;
import com.chunyu.pregnancy.common.exception.BizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * 固定一分钟窗口限流。Lua让计数和设置TTL原子完成，避免INCR成功而EXPIRE失败产生永久键。
 * 首版按连接IP限流，不直接相信客户端传来的X-Forwarded-For。
 */
// 通用 Spring 组件：由容器创建并管理，其他类可通过构造器使用它。
@Component
public class LoginRateLimiter {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(
            "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n", Long.class);

    public LoginRateLimiter(StringRedisTemplate redis) { this.redis = redis; }

    public void check(String action, String remoteAddress) {
        Long count;
        try {
            String key = "cy:auth:rate:" + action + ":" + DigestUtils.sha256(remoteAddress);
            count = redis.execute(script, Collections.singletonList(key));
        } catch (RuntimeException ex) {
            throw new BizException(503, "AUTH_SERVICE_UNAVAILABLE", "登录服务暂不可用");
        }
        if (count == null) { throw new BizException(503, "AUTH_SERVICE_UNAVAILABLE", "登录服务暂不可用"); }
        if (count > 30) { throw new BizException(429, "RATE_LIMITED", "操作频繁，请一分钟后重试"); }
    }
}
