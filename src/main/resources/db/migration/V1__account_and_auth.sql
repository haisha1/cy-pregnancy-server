-- 第一阶段只创建已实现的账号/鉴权表；后续业务使用新版本迁移追加，禁止修改已执行的 V1。
CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    app_id VARCHAR(64) NOT NULL,
    subject_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_status VARCHAR(16) NOT NULL DEFAULT 'active',
    cloud_data_epoch BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_wx_subject (app_id, subject_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='小程序用户；不包含健康数据';

CREATE TABLE auth_session (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    client_uuid CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id BIGINT NOT NULL,
    installation_id VARCHAR(64) NOT NULL,
    refresh_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_session_uuid (client_uuid),
    UNIQUE KEY uk_refresh_hash (refresh_token_hash),
    KEY ix_user_session (user_id, revoked_at),
    CONSTRAINT fk_session_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录会话；只存续期令牌散列';

-- 已使用 refresh token 的散列用于识别重放。会话到期/清理时一并删除，不保留明文凭据。
CREATE TABLE refresh_token_history (
    token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    used_at DATETIME(6) NOT NULL,
    KEY ix_history_session (session_id),
    CONSTRAINT fk_history_session FOREIGN KEY (session_id) REFERENCES auth_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
