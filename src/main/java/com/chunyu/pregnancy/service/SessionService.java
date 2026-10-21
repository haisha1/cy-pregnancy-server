package com.chunyu.pregnancy.service;

import com.chunyu.pregnancy.mapper.AuthSessionMapper;
import org.springframework.stereotype.Service;

/** 会话有效性查询入口，让安全过滤器通过Service访问持久化层。 */
// 业务层组件：由 Spring 管理，负责组织业务步骤；事务边界仍需由事务代码明确控制。
@Service
public class SessionService {
    private final AuthSessionMapper sessions;

    // 单一构造器由 Spring 自动注入依赖；final 字段表明这些依赖在构造后不再替换。
    public SessionService(AuthSessionMapper sessions) { this.sessions = sessions; }

    /** 同时检查会话归属、撤销/到期和账户状态；数据库不可用时由过滤器返回503。 */
    public boolean isActive(Long userId, String sessionUuid) {
        return sessions.countActive(sessionUuid, userId) == 1;
    }
}
