-- CampusQuest 演示数据（阶段 12 验收造数，可重复执行）
-- 三个账号密码均为 123456
-- A：信用 100，已发布 3 条待接单任务，余额 40
-- B：信用 89，无任务（信用低于 90 不能发布，可接单）
-- C：信用 84，无任务（低于 85 不能接单）
-- 所有积分/信用流水与余额严格对应，便于账实核对。

USE campus_quest;

SET @pwd = '4494b30086e68e816b7a3cbffb20fcd0ef8fced63f6dab34b49f2ddfda126e48';

INSERT INTO users (phone, nickname, password_hash, points, credit)
SELECT '13800000001', '演示同学A', @pwd, 40, 100
WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone = '13800000001');

INSERT INTO users (phone, nickname, password_hash, points, credit)
SELECT '13800000002', '演示同学B', @pwd, 100, 89
WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone = '13800000002');

INSERT INTO users (phone, nickname, password_hash, points, credit)
SELECT '13800000003', '演示同学C', @pwd, 100, 84
WHERE NOT EXISTS (SELECT 1 FROM users WHERE phone = '13800000003');

-- A：3 条待接单任务（发布时扣 20 积分，余额 40）
INSERT INTO tasks (task_no, category, title, description, points, location_desc, time_desc, publisher_id, status, created_at)
SELECT CONCAT('CQ-', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-901'), 'EXPRESS', '演示任务一：代取东门快递',
       '菜鸟驿站的快递，取件码会发在聊天里，送到 3 号宿舍楼一楼货架即可。', 20,
       '东门菜鸟驿站 → 3 号宿舍楼', '今天 18:00 前', u.id, 'WAITING',
       DATE_SUB(NOW(), INTERVAL 2 DAY)
FROM users u WHERE u.phone = '13800000001'
  AND NOT EXISTS (SELECT 1 FROM tasks WHERE title = '演示任务一：代取东门快递');

INSERT INTO tasks (task_no, category, title, description, points, location_desc, time_desc, publisher_id, status, created_at)
SELECT CONCAT('CQ-', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-902'), 'OTHER', '演示任务二：打印装订课程报告',
       '打印 30 份课程报告并装订，费用另算，材料钱当面结清。', 20,
       '图书馆打印店 → 教学楼 B 座 302', '明天 12:00 前', u.id, 'WAITING',
       DATE_SUB(NOW(), INTERVAL 1 DAY)
FROM users u WHERE u.phone = '13800000001'
  AND NOT EXISTS (SELECT 1 FROM tasks WHERE title = '演示任务二：打印装订课程报告');

INSERT INTO tasks (task_no, category, title, description, points, location_desc, time_desc, publisher_id, status, created_at)
SELECT CONCAT('CQ-', DATE_FORMAT(CURDATE(), '%Y%m%d'), '-903'), 'MOVING', '演示任务三：帮忙搬两箱书到 5 号楼',
       '两箱书不重，需要从小推车搬到 5 号楼 402，大约二十分钟。', 20,
       '2 号宿舍楼 → 5 号楼 402', '本周六上午', u.id, 'WAITING',
       NOW()
FROM users u WHERE u.phone = '13800000001'
  AND NOT EXISTS (SELECT 1 FROM tasks WHERE title = '演示任务三：帮忙搬两箱书到 5 号楼');

-- 积分流水：注册赠送 +100，A 发布 3 条各 -20
INSERT INTO point_logs (user_id, change_amount, balance_after, type, remark)
SELECT u.id, 100, 100, 'REGISTER', '注册赠送'
FROM users u WHERE u.phone IN ('13800000001', '13800000002', '13800000003')
  AND NOT EXISTS (SELECT 1 FROM point_logs p WHERE p.user_id = u.id);

INSERT INTO point_logs (user_id, change_amount, balance_after, type, related_task_id, remark)
SELECT u.id, -20, 80, 'PUBLISH', t.id, '发布任务扣除预算'
FROM users u JOIN tasks t ON t.publisher_id = u.id AND t.title = '演示任务一：代取东门快递'
WHERE u.phone = '13800000001'
  AND NOT EXISTS (SELECT 1 FROM point_logs p WHERE p.user_id = u.id AND p.type = 'PUBLISH' AND p.related_task_id = t.id);

INSERT INTO point_logs (user_id, change_amount, balance_after, type, related_task_id, remark)
SELECT u.id, -20, 60, 'PUBLISH', t.id, '发布任务扣除预算'
FROM users u JOIN tasks t ON t.publisher_id = u.id AND t.title = '演示任务二：打印装订课程报告'
WHERE u.phone = '13800000001'
  AND NOT EXISTS (SELECT 1 FROM point_logs p WHERE p.user_id = u.id AND p.type = 'PUBLISH' AND p.related_task_id = t.id);

INSERT INTO point_logs (user_id, change_amount, balance_after, type, related_task_id, remark)
SELECT u.id, -20, 40, 'PUBLISH', t.id, '发布任务扣除预算'
FROM users u JOIN tasks t ON t.publisher_id = u.id AND t.title = '演示任务三：帮忙搬两箱书到 5 号楼'
WHERE u.phone = '13800000001'
  AND NOT EXISTS (SELECT 1 FROM point_logs p WHERE p.user_id = u.id AND p.type = 'PUBLISH' AND p.related_task_id = t.id);

-- 信用流水：初始 100，B 调到 89，C 调到 84
INSERT INTO credit_logs (user_id, change_amount, reason, remark)
SELECT u.id, 100, 'INIT', '初始信用'
FROM users u WHERE u.phone IN ('13800000001', '13800000002', '13800000003')
  AND NOT EXISTS (SELECT 1 FROM credit_logs c WHERE c.user_id = u.id);

INSERT INTO credit_logs (user_id, change_amount, reason, remark)
SELECT u.id, -11, 'SEED_DEMO', '演示数据：信用调整至 89'
FROM users u WHERE u.phone = '13800000002'
  AND NOT EXISTS (SELECT 1 FROM credit_logs c WHERE c.user_id = u.id AND c.reason = 'SEED_DEMO');

INSERT INTO credit_logs (user_id, change_amount, reason, remark)
SELECT u.id, -16, 'SEED_DEMO', '演示数据：信用调整至 84'
FROM users u WHERE u.phone = '13800000003'
  AND NOT EXISTS (SELECT 1 FROM credit_logs c WHERE c.user_id = u.id AND c.reason = 'SEED_DEMO');
