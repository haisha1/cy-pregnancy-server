-- 账号展示资料与健康档案分离；头像只允许本人通过鉴权接口读取，不建立公开图片URL。
ALTER TABLE app_user
    ADD COLUMN nickname VARCHAR(32) NULL,
    ADD COLUMN avatar_jpeg MEDIUMBLOB NULL,
    ADD COLUMN profile_revision BIGINT NOT NULL DEFAULT 0;
