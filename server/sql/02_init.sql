-- CampusQuest 初始化脚本：管理员账号
-- 管理员登录名 admin，密码 admin123
-- 密码规则：SHA-256(明文 + 固定盐 "CQ_SALT_2026") 的小写十六进制
-- 修改密码方式：直接用下面这行 SQL 结构替换 password_hash（先按同一算法算出新哈希）：
--   UPDATE admins SET password_hash = '<新哈希>' WHERE username = 'admin';

USE campus_quest;

INSERT INTO admins (username, password_hash)
SELECT 'admin', 'a011feccb53ccaee2bd34c4374fe3237c289d04cb4d841f66caa20fd4254ca95'
WHERE NOT EXISTS (SELECT 1 FROM admins WHERE username = 'admin');
