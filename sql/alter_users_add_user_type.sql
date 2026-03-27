-- 用户类型：0=普通用户（账号密码等） 1=微信用户
-- 在库 campus_lost_found 执行；若列已存在会报错，忽略或先检查再执行。

ALTER TABLE users ADD COLUMN user_type TINYINT NOT NULL DEFAULT 0;

UPDATE users SET user_type = 1 WHERE openid IS NOT NULL AND TRIM(openid) <> '';

UPDATE users SET user_type = 0 WHERE openid IS NULL OR TRIM(openid) = '';
