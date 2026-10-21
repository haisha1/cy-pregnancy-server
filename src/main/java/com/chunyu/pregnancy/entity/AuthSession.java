package com.chunyu.pregnancy.entity;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 一条可撤销登录会话；数据库是权威状态，JWT未到期也可能被撤销。 实体只用于持久化，控制器返回独立DTO。 */
// Lombok 在编译期生成 getter；IDE 需要启用注解处理，运行时不靠反射生成方法。
@Getter
// Lombok 在编译期生成 setter，减少字段赋值方法的重复代码。
@Setter
// 指定实体对应的数据库表名；这是映射声明，不负责自动创建表。
@TableName("auth_session")
public class AuthSession {
    // 标记数据库主键及生成策略；AUTO 表示使用数据库自增主键。
    @TableId(type = IdType.AUTO)
    private Long id;
    private String clientUuid;
    private Long userId;
    private String installationId;
    // 只存刷新凭据的散列；明文仅在签发时返回客户端。
    private String refreshTokenHash;
    // 会话绝对到期时间，轮换刷新令牌时不向后延长。
    private Instant expiresAt;
    private Instant revokedAt;
    private Instant createdAt;
}
