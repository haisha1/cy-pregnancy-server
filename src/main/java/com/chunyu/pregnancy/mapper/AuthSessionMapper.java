package com.chunyu.pregnancy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chunyu.pregnancy.entity.AuthSession;
import org.apache.ibatis.annotations.*;

/** refresh必须锁行；否则两次并发续期都可能成功并产生互相覆盖的令牌。 */
public interface AuthSessionMapper extends BaseMapper<AuthSession> {
    // 声明查询 SQL；#{参数} 使用参数绑定，不把外部输入直接拼成 SQL 结构。
    @Select("SELECT * FROM auth_session WHERE refresh_token_hash=#{hash} FOR UPDATE")
    AuthSession lockByRefreshHash(@Param("hash") String hash);

    @Select("SELECT COUNT(*) FROM auth_session s JOIN app_user u ON u.id=s.user_id " +
            "WHERE s.client_uuid=#{uuid} AND s.user_id=#{userId} AND s.revoked_at IS NULL " +
            "AND s.expires_at>UTC_TIMESTAMP(6) AND u.account_status='active'")
    int countActive(@Param("uuid") String uuid, @Param("userId") Long userId);

    // 声明插入 SQL；参数通过 MyBatis 绑定，事务由调用它的业务层控制。
    @Insert("INSERT INTO refresh_token_history(token_hash,session_id,used_at) VALUES(#{hash},#{sessionId},UTC_TIMESTAMP(6))")
    void recordUsedRefresh(@Param("hash") String hash, @Param("sessionId") Long sessionId);

    @Select("SELECT session_id FROM refresh_token_history WHERE token_hash=#{hash}")
    Long findReplayedSession(@Param("hash") String hash);

    // 声明更新 SQL；需要结合 WHERE 条件理解具体更新的记录范围。
    @Update("UPDATE auth_session SET revoked_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND revoked_at IS NULL")
    void revokeById(@Param("id") Long id);

    @Update("UPDATE auth_session SET revoked_at=UTC_TIMESTAMP(6) WHERE client_uuid=#{uuid} AND user_id=#{userId} AND revoked_at IS NULL")
    void revokeOwned(@Param("uuid") String uuid, @Param("userId") Long userId);
}
