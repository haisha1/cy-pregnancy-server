package com.chunyu.pregnancy.service;

import com.chunyu.pregnancy.mapper.AuthSessionMapper;
import org.springframework.stereotype.Service;

/** 会话有效性查询入口，让安全过滤器通过Service访问持久化层。 */
@Service
public class SessionService {
    private final AuthSessionMapper sessions;

    public SessionService(AuthSessionMapper sessions) { this.sessions = sessions; }

    /** 同时检查会话归属、撤销/到期和账户状态；数据库不可用时由过滤器返回503。 */
    public boolean isActive(Long userId, String sessionUuid) {
        return sessions.countActive(sessionUuid, userId) == 1;
    }
}
