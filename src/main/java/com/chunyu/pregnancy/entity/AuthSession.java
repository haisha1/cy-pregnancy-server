package com.chunyu.pregnancy.entity;

import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** 一条可撤销登录会话；数据库是权威状态，JWT未到期也可能被撤销。 实体只用于持久化，控制器返回独立DTO。 */
@Getter
@Setter
@TableName("auth_session")
public class AuthSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String clientUuid;
    private Long userId;
    private String installationId;
    private String refreshTokenHash;
    private Instant expiresAt;
    private Instant revokedAt;
    private Instant createdAt;
}
