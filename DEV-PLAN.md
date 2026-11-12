# CampusQuest 开发计划（DEV-PLAN）

| 项目 | 内容 |
| --- | --- |
| 依据文档 | e:\MyApp\PRD.md（v1.0） |
| 文档用途 | 可直接照着做的施工顺序，细到建表、写类、写接口、页面接接口 |
| 技术形态 | 原生 Servlet + JDBC + DAO + Filter；前端 Vue2 + ElementUI + axios，无脚手架 |
| 运行形态 | Windows 单机，Tomcat 9 + MySQL 8.0.46，浏览器访问，全离线运行 |
| 阶段数 | 12 个阶段，总计约 17.5 人天（单人全职，含自测） |

本文所有功能与数字都以 PRD 为准。PRD 没写的功能，本文不排期、不建表、不开接口。

---

## 0. 怎么用这份计划

1. 先看第 1 节，确认三个决策点；决策点没定，阶段 1 不要开工。
2. 阶段之间是硬依赖，不要跳阶段并行。每个阶段结束时必须能启动服务、能点页面、控制台无异常。
3. 每个阶段都有「完成判定」和「自测清单」，勾完再进入下一阶段。
4. 表结构、接口、响应格式以第 2、3、4 节为唯一口径，代码与文档不一致时改代码。

---

## 1. 开工前必须先定的决策点

这三项已于开工前确认，按下列默认值执行（已定）。

| 编号 | 决策点 | 已定结论 | 影响范围 |
| --- | --- | --- | --- |
| D1 | 数据库是否新建 campus_quest 库 | 新建专用库 `campus_quest`，字符集 utf8mb4，排序 utf8mb4_0900_ai_ci，不与本机其他库混用 | 阶段 2 全部建表脚本、JDBC 连接串 |
| D2 | 是否使用免安装 Tomcat 9 | 使用 apache-tomcat-9.0.123 免安装包，解压到 `E:\MyApp\tools\tomcat9`，端口 8080 | 阶段 1 部署方式、所有页面 URL |
| D3 | 是否允许开发期联网下载一次依赖 | 允许一次，只在阶段 1 下载：mysql-connector-j 8.0.33、gson 2.10.1、Vue 2.6.14、ElementUI 2.15.14、axios 1.6.8；已全部落本地，运行期不请求外网 | 阶段 1 依赖准备、阶段 12 离线验收 |

补充约束（已确认事实，不再讨论）：

- 本机未安装 Maven，不用 Maven 构建，jar 直接放 `WEB-INF\lib`，用 `javac` 编译。
- 本机未安装 Tomcat，用免安装版；必须是 Tomcat 9（javax.servlet 命名空间），不能用 Tomcat 10/11。
- Java 11，编译与运行都用 11。
- MySQL 8.0.46 已安装，服务名 MySQL80，地址 localhost:3306。

---

## 2. 全局技术约定

### 2.1 目录结构

```
E:\MyApp\
  PRD.md
  DEV-PLAN.md
  uploads\                        运行期图片根目录（不进 webapp）
    2026\11\09\*.jpg
  backup\                         手工备份输出目录
  tools\tomcat9\                  免安装 Tomcat 9
    conf\Catalina\localhost\campusquest.xml   指向 docBase
  server\
    sql\01_schema.sql             建库建表
    sql\02_init.sql               管理员账号 + 基础字典校验
    sql\03_seed.sql               演示数据（可选，验收用）
    build.ps1                     javac 编译脚本
    start.ps1 / stop.ps1          启停脚本
    src\main\java\com\campusquest\
      util\      DBUtil, JsonUtil, Resp, PageResult, SessionUtil, FileUtil, TextUtil
      entity\    User, Admin, Task, Application, Conversation, Message, Review,
                 PointLog, CreditLog, Appeal, Notification
      vo\        TaskCardVO, TaskDetailVO, MyTaskVO, StatsVO, LoginVO ...
      dao\       BaseDao, UserDao, AdminDao, TaskDao, ApplicationDao, ConversationDao,
                 MessageDao, ReviewDao, PointLogDao, CreditLogDao, AppealDao,
                 NotificationDao, StatDao
      service\   AuthService, TaskService, ApplicationService, SettlementService,
                 PointService, CreditService, ChatService, NotificationService,
                 StatService, AppealService, AdminTaskService
      filter\    EncodingFilter, CorsFilter, AutoSettleFilter, AuthFilter
      servlet\   BaseServlet, AuthServlet, TaskServlet, ConversationServlet,
                 NotificationServlet, PointServlet, CreditServlet, AppealServlet,
                 StatsServlet, AdminAppealServlet, AdminTaskServlet, FileServlet
    src\main\webapp\              Tomcat docBase
      login.html dashboard.html lobby.html task-detail.html publish.html
      my-tasks.html messages.html profile.html leaderboard.html
      admin\appeals.html admin\tasks.html
      assets\css\app.css
      assets\lib\vue.min.js
      assets\lib\axios.min.js
      assets\lib\element-ui\index.js
      assets\lib\element-ui\theme-chalk\index.css
      assets\lib\element-ui\theme-chalk\fonts\*
      assets\js\core.js             axios 封装、字典、格式化
      assets\js\components.js       全局组件（x-template）
      assets\js\page\*.js           每个页面一个脚本
      WEB-INF\web.xml
      WEB-INF\lib\mysql-connector-j-8.0.33.jar
      WEB-INF\lib\gson-2.10.1.jar
      WEB-INF\classes\              编译输出
```

URL 约定：页面 `http://localhost:8080/campusquest/lobby.html`，接口 `http://localhost:8080/campusquest/api/tasks`。页面与接口同源，正常不需要跨域，CorsFilter 只为将来拆分端口预留。

### 2.2 统一响应格式

所有接口返回 `application/json;charset=UTF-8`，HTTP 状态除 401/403/404/405 外一律 200，业务错误靠 code 区分。

```json
{ "code": 0, "message": "ok", "data": {} }
```

- `code`：0 表示成功，非 0 表示失败。
- `message`：失败时必须是可直接展示给用户的中文句子，例如「积分不足，当前余额 10，本次需要 20」。
- `data`：成功时的业务数据；无数据时返回 `{}` 或 `null`，不省略字段。

分页统一结构：

```json
{ "code": 0, "message": "ok",
  "data": { "page": 1, "size": 12, "total": 37, "pages": 4, "list": [] } }
```

`page` 从 1 开始，`size` 默认 12、最大 50，`pages` 由后端算，`total` 为满足条件的总条数。

### 2.3 错误码表

| code | HTTP | 含义 | 典型文案 |
| --- | --- | --- | --- |
| 0 | 200 | 成功 | ok |
| 1001 | 200 | 参数校验失败 | 标题不能为空，最多 60 个字 |
| 1002 | 401 | 未登录或会话失效 | 登录已失效，请重新登录 |
| 1003 | 403 | 无权限 | 没有权限执行该操作 |
| 1004 | 404 | 资源不存在 | 任务不存在或已删除 |
| 1005 | 405 | 请求方法不支持 | 不支持的请求方式 |
| 2001 | 200 | 手机号已注册 | 该手机号已注册，请直接登录 |
| 2002 | 200 | 手机号格式错误 | 请输入 11 位手机号 |
| 2003 | 200 | 手机号或密码错误 | 手机号或密码不正确 |
| 2004 | 200 | 管理员账号或密码错误 | 管理员账号或密码不正确 |
| 3001 | 200 | 积分不足 | 积分不足，当前余额 10，本次需要 20 |
| 3002 | 200 | 信用分不足不可发布 | 信用分低于 90，暂不能发布任务 |
| 3003 | 200 | 信用分不足不可接单 | 信用分低于 85，不能接单 |
| 3004 | 200 | 状态不允许该操作 | 当前任务状态不支持该操作 |
| 3005 | 200 | 不能申请自己发布的任务 | 不能申请自己发布的任务 |
| 3006 | 200 | 重复申请 | 你已经申请过这个任务 |
| 3007 | 200 | 任务已被接单 | 该任务已被其他同学接单 |
| 3008 | 200 | 申请已失效 | 该申请已失效 |
| 3009 | 200 | 重复评价 | 你已经评价过这个任务 |
| 3010 | 200 | 未到可评价阶段 | 任务完成后才能评价 |
| 3011 | 200 | 重复申诉 | 该扣分已提交过申诉 |
| 3012 | 200 | 申诉已处理 | 该申诉已处理，不能重复操作 |
| 4001 | 200 | 文件类型不支持 | 只支持 jpg、jpeg、png、gif、webp 图片 |
| 4002 | 200 | 文件过大 | 图片不能超过 5MB |
| 4003 | 200 | 缺少文件 | 请选择要上传的图片 |
| 4004 | 200 | 文件保存失败 | 图片保存失败，请重试 |
| 5000 | 200 | 服务器内部错误 | 服务器开小差了，请稍后再试 |
| 5001 | 200 | 数据库操作失败 | 数据操作失败，请稍后再试 |

### 2.4 鉴权与角色

| 项 | 规则 |
| --- | --- |
| 会话载体 | HttpSession，`JSESSIONID` Cookie，后端内存保存，重启后需重新登录 |
| Session 属性 | `role`（STUDENT / ADMIN）、`userId`、`adminId`、`nickname` |
| 学生接口 | `/api/**` 除登录注册外，全部要求 `role=STUDENT` |
| 管理员接口 | `/api/admin/**` 要求 `role=ADMIN`，学生访问返回 1003 并跳登录页 |
| 免登录白名单 | `POST /api/auth/register`、`POST /api/auth/login`、`POST /api/auth/admin/login`、`GET /api/ping` |
| 越权数据 | 改他人任务、读他人会话、确认非本人任务，一律 1003 |

### 2.5 存储编码与时间

- 数据库：`utf8mb4` / `utf8mb4_0900_ai_ci`，所有表 `ENGINE=InnoDB`。
- JDBC URL：`jdbc:mysql://localhost:3306/campus_quest?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true`
- 连接账号：建议单独建 `cq_app` 账号，只授权 campus_quest 库；若嫌麻烦先用 root，但要在 SQL 里写清。
- 时间：全部用 `DATETIME`，由数据库 `NOW()` 生成，Java 侧不再 `new Date()` 参与业务判断，避免时区差 8 小时。
- 金额与积分：整数，不用浮点。充值金额用整数元。

### 2.6 固定字典

| 字典 | 存储值 | 显示文案 |
| --- | --- | --- |
| 分类 | EXPRESS / TAKEOUT / ERRAND / MOVING / CLEANING / OTHER | 取快递 / 代取外卖 / 代替服务 / 搬运服务 / 宿舍打扫 / 其他 |
| 任务状态 | WAITING / IN_PROGRESS / PENDING_CONFIRM / COMPLETED / CANCELLED | 待接单 / 进行中 / 待确认完成 / 已完成 / 已取消 |
| 申请状态 | PENDING / SELECTED / REJECTED / ABANDONED | 待确认 / 已选中 / 未选中 / 已放弃 |
| 申诉状态 | PENDING / APPROVED / REJECTED | 待处理 / 已通过 / 已驳回 |
| 评价 | GOOD / BAD | 好评 / 差评 |
| 积分流水类型 | REGISTER / PUBLISH / REFUND / INCOME / RECHARGE | 注册赠送 / 发布任务 / 取消退回 / 任务收入 / 模拟充值 |
| 信用流水原因 | INIT / GOOD_REVIEW / BAD_REVIEW / ABANDON / APPEAL_REFUND | 初始信用 / 收到好评 / 收到差评 / 超时放弃 / 申诉退回 |
| 通知类型 | APPLICATION_RECEIVED / APPLICATION_CONFIRMED / REVIEW_RECEIVED / SETTLEMENT / APPEAL_RESULT | 收到申请 / 被确认 / 收到评价 / 结算到账 / 申诉结果 |

前端 `core.js` 里维护上述映射，页面不出现英文码。

---

## 3. 数据库设计

### 3.1 建库

```sql
CREATE DATABASE IF NOT EXISTS campus_quest
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE campus_quest;
```

以下 11 张表。主键统一 `INT UNSIGNED AUTO_INCREMENT`。不使用物理外键，全部用逻辑关联 + 索引，理由：删除与恢复简单，避免建表顺序耦合。关联关系见表后说明。

### 3.2 users（学生账号）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | INT UNSIGNED | PK, AUTO_INCREMENT | |
| phone | CHAR(11) | NOT NULL, UNIQUE `uk_phone` | 11 位数字，登录账号 |
| nickname | VARCHAR(32) | NOT NULL | 昵称 |
| password_hash | CHAR(64) | NOT NULL | SHA-256(明文 + 固定盐) 的十六进制 |
| points | INT | NOT NULL DEFAULT 100 | 当前积分，不允许为负 |
| credit | INT | NOT NULL DEFAULT 100 | 当前信用分，0–100 |
| avatar_path | VARCHAR(255) | NULL | 头像相对路径，第一版不提供上传入口，留空 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：`uk_phone(phone)`。

### 3.3 admins（管理员）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | INT UNSIGNED | PK, AUTO_INCREMENT | |
| username | VARCHAR(32) | NOT NULL, UNIQUE `uk_username` | 管理员登录名 |
| password_hash | CHAR(64) | NOT NULL | 与 users 同一套哈希方式 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | |

初始化插入一条：`admin` / `admin123`（哈希值写在 02_init.sql 里）。第一版只有一个管理员账号，不提供注册与改密界面。

### 3.4 tasks（任务）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | INT UNSIGNED | PK, AUTO_INCREMENT | |
| task_no | VARCHAR(20) | NOT NULL, UNIQUE `uk_task_no` | 格式 CQ-yyyyMMdd-三位序号 |
| category | VARCHAR(16) | NOT NULL | 六个固定值之一 |
| title | VARCHAR(60) | NOT NULL | 标题，前端两行截断 |
| description | TEXT | NOT NULL | 描述 |
| points | INT | NOT NULL | 积分预算，1–999 |
| location_desc | VARCHAR(255) | NOT NULL | 地点说明 |
| time_desc | VARCHAR(255) | NOT NULL | 时间说明 |
| publisher_id | INT UNSIGNED | NOT NULL | 关联 users.id |
| acceptor_id | INT UNSIGNED | NULL | 确认接单后写入 |
| status | VARCHAR(20) | NOT NULL DEFAULT 'WAITING' | 五种状态 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| accepted_at | DATETIME | NULL | 确认接单时间，放弃扣分判断基准 |
| delivered_at | DATETIME | NULL | 标记送达时间 |
| confirm_deadline | DATETIME | NULL | delivered_at + 24 小时 |
| finished_at | DATETIME | NULL | 完成时间 |
| offline | TINYINT | NOT NULL DEFAULT 0 | 0 正常，1 已下架 |
| offline_reason | VARCHAR(255) | NULL | 下架理由 |
| offline_at | DATETIME | NULL | 下架时间 |

索引：`idx_status_created(status, created_at)`、`idx_publisher(publisher_id, status)`、`idx_acceptor(acceptor_id, status)`、`idx_category(category)`、`idx_deadline(status, confirm_deadline)`、`idx_offline(offline)`。

### 3.5 applications（申请接单）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | INT UNSIGNED | PK, AUTO_INCREMENT | |
| task_id | INT UNSIGNED | NOT NULL | 关联 tasks.id |
| applicant_id | INT UNSIGNED | NOT NULL | 关联 users.id |
| status | VARCHAR(16) | NOT NULL DEFAULT 'PENDING' | PENDING / SELECTED / REJECTED / ABANDONED |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | 最近一次申请时间 |
| abandoned_at | DATETIME | NULL | 放弃时间 |
| penalty_applied | TINYINT | NOT NULL DEFAULT 0 | 该次放弃是否已扣 2 分，防重复扣 |

索引：`uk_task_applicant(task_id, applicant_id)`（一人一条记录；放弃后重新申请复用该行，状态改回 PENDING）、`idx_applicant(applicant_id, status)`。

### 3.6 conversations（聊天会话）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | INT UNSIGNED | PK, AUTO_INCREMENT | |
| task_id | INT UNSIGNED | NOT NULL, UNIQUE `uk_task` | 一个任务只建一个会话 |
| publisher_id | INT UNSIGNED | NOT NULL | 发布者 |
| acceptor_id | INT UNSIGNED | NOT NULL | 当前接单者，放弃后仍保留原值 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | |

### 3.7 messages（聊天消息）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | INT UNSIGNED | PK, AUTO_INCREMENT | 兼作增量拉取游标 |
| conversation_id | INT UNSIGNED | NOT NULL | 关联 conversations.id |
| sender_id | INT UNSIGNED | NOT NULL | 发送者 |
| msg_type | VARCHAR(8) | NOT NULL | text / image |
| content | TEXT | NULL | 文字内容，image 消息为 NULL |
| image_path | VARCHAR(255) | NULL | 相对路径，如 uploads/2026/11/09/xxx.jpg |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：`idx_conv(conversation_id, id)`。

### 3.8 reviews（互评）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | INT UNSIGNED | PK, AUTO_INCREMENT | |
| task_id | INT UNSIGNED | NOT NULL | 关联 tasks.id |
| from_user_id | INT UNSIGNED | NOT NULL | 评价人 |
| to_user_id | INT UNSIGNED | NOT NULL | 被评价人 |
| rating | VARCHAR(8) | NOT NULL | GOOD / BAD |
| comment | VARCHAR(255) | NULL | 文字说明，可空 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：`uk_task_from(task_id, from_user_id)`（一方对同一任务只能评一次）、`idx_to(to_user_id)`。

### 3.9 point_logs（积分流水）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | INT UNSIGNED | PK, AUTO_INCREMENT | |
| user_id | INT UNSIGNED | NOT NULL | |
| change_amount | INT | NOT NULL | 正数收入，负数支出 |
| balance_after | INT | NOT NULL | 变动后余额，用于对账 |
| type | VARCHAR(16) | NOT NULL | 五种类型 |
| related_task_id | INT UNSIGNED | NULL | 关联任务 |
| remark | VARCHAR(255) | NOT NULL | 说明，如任务标题、充值金额 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：`idx_user(user_id, id)`。

### 3.10 credit_logs（信用流水）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | INT UNSIGNED | PK, AUTO_INCREMENT | |
| user_id | INT UNSIGNED | NOT NULL | |
| change_amount | INT | NOT NULL | +3 / −3 / −2 / +2 / +100(初始) |
| reason | VARCHAR(16) | NOT NULL | 五种原因 |
| related_task_id | INT UNSIGNED | NULL | 关联任务 |
| remark | VARCHAR(255) | NULL | 补充说明，如「已封顶」 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：`idx_user(user_id, id)`。

### 3.11 appeals（申诉）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | INT UNSIGNED | PK, AUTO_INCREMENT | |
| user_id | INT UNSIGNED | NOT NULL | 申诉人（被扣分的接单者） |
| task_id | INT UNSIGNED | NOT NULL | 关联任务 |
| content | VARCHAR(500) | NOT NULL | 申诉说明 |
| screenshot_path | VARCHAR(255) | NOT NULL | 聊天截图相对路径 |
| status | VARCHAR(16) | NOT NULL DEFAULT 'PENDING' | 三种状态 |
| reject_reason | VARCHAR(255) | NULL | 驳回理由，驳回时必填 |
| handled_by | INT UNSIGNED | NULL | 处理的管理员 id |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | |
| handled_at | DATETIME | NULL | 处理时间 |

索引：`uk_user_task(user_id, task_id)`（每条扣分只能申诉一次）、`idx_status(status, created_at)`。

### 3.12 notifications（系统通知）

| 字段 | 类型 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | INT UNSIGNED | PK, AUTO_INCREMENT | |
| user_id | INT UNSIGNED | NOT NULL | 接收人 |
| type | VARCHAR(32) | NOT NULL | 五种类型 |
| title | VARCHAR(64) | NOT NULL | 标题 |
| content | VARCHAR(255) | NOT NULL | 正文 |
| related_task_id | INT UNSIGNED | NULL | 关联任务，可点击跳详情 |
| is_read | TINYINT | NOT NULL DEFAULT 0 | 0 未读，1 已读 |
| created_at | DATETIME | NOT NULL DEFAULT CURRENT_TIMESTAMP | |

索引：`idx_user_read(user_id, is_read, id)`。

### 3.13 逻辑关联关系

| 关系 | 说明 |
| --- | --- |
| tasks.publisher_id → users.id | 一个用户发布多个任务 |
| tasks.acceptor_id → users.id | 一个用户可接多个任务，放弃后清空 |
| applications.task_id → tasks.id，applications.applicant_id → users.id | 一任务多申请，一用户一任务一条 |
| conversations.task_id → tasks.id（唯一） | 确认接单时创建，放弃不清除 |
| messages.conversation_id → conversations.id | 会话内消息按 id 递增 |
| reviews.task_id → tasks.id，from/to → users.id | 完成任务后双方各一条 |
| point_logs.user_id / credit_logs.user_id → users.id | 余额变动与流水同事务写入 |
| appeals.user_id → users.id，appeals.task_id → tasks.id | 一次放弃扣分对应一条申诉 |
| notifications.user_id → users.id | 未读数由此表统计 |

### 3.14 状态流转（实现对照表）

| 当前状态 | 动作 | 结果 | 必须同时发生 |
| --- | --- | --- | --- |
| WAITING | 发布者取消 | CANCELLED | 全额退积分 + REFUND 流水 + 通知发起人 |
| WAITING | 发布者确认某申请 | IN_PROGRESS | 写 acceptor_id、accepted_at；目标申请 SELECTED，其余 PENDING 改 REJECTED；创建会话 |
| IN_PROGRESS | 接单者放弃（≤30 分钟） | WAITING | acceptor_id 置空，申请 ABANDONED，不扣分 |
| IN_PROGRESS | 接单者放弃（>30 分钟） | WAITING | 同上 + credit −2 + ABANDON 流水 + penalty_applied=1 |
| IN_PROGRESS | 接单者送达 | PENDING_CONFIRM | delivered_at=NOW()，confirm_deadline=+24h |
| PENDING_CONFIRM | 发布者确认 / 系统自动 | COMPLETED | finished_at，接单者 +预算，INCOME 流水，结算通知 |
| 任意 | 管理员下架 | 状态不变 | offline=1 + 理由 + 时间 |
| 已下架 | 管理员恢复 | 状态不变 | offline=0，清空下线时间与理由 |

---

## 4. 接口总表

权限列：公开 = 免登录；学生 = 需学生会话；管理员 = 需管理员会话。所有接口前缀 `/api`。入参除标注 multipart 外均为 JSON。

### 4.1 认证 auth

| 方法 | 路径 | 入参 | 返回 data 主要字段 | 权限 |
| --- | --- | --- | --- | --- |
| POST | /api/auth/register | phone, nickname, password | userId | 公开 |
| POST | /api/auth/login | phone, password | userId, nickname, points, credit, role=STUDENT | 公开 |
| POST | /api/auth/admin/login | username, password | adminId, username, role=ADMIN | 公开 |
| POST | /api/auth/logout | 无 | 无 | 学生/管理员 |
| GET | /api/auth/me | 无 | userId, nickname, phoneMasked, points, credit, unreadCount, avatarPath | 学生 |
| GET | /api/ping | 无 | time | 公开 |

### 4.2 任务 tasks

| 方法 | 路径 | 入参 | 返回 data 主要字段 | 权限 |
| --- | --- | --- | --- | --- |
| GET | /api/tasks | category, keyword, sort=points_desc/points_asc/latest, onlyWaiting, page, size | 分页 + list[TaskCardVO] | 学生 |
| POST | /api/tasks | category, title, description, points, locationDesc, timeDesc | id, taskNo, points | 学生 |
| GET | /api/tasks/{id} | 无 | TaskDetailVO | 学生 |
| GET | /api/tasks/{id}/applications | 无 | list[applicationId, applicantId, nickname, credit, status, createdAt] | 学生 |
| POST | /api/tasks/{id}/apply | 无 | applicationId | 学生 |
| POST | /api/tasks/{id}/confirm | applicationId | 无 | 学生（发布者） |
| POST | /api/tasks/{id}/deliver | 无 | confirmDeadline | 学生（接单者） |
| POST | /api/tasks/{id}/complete | 无 | finishedAt | 学生（发布者） |
| POST | /api/tasks/{id}/cancel | 无 | refundPoints | 学生（发布者） |
| POST | /api/tasks/{id}/abandon | 无 | penaltyApplied, credit | 学生（接单者） |
| GET | /api/tasks/mine | role=published/accepted, status, page, size | 分页 + list[MyTaskVO] | 学生 |
| POST | /api/tasks/{id}/reviews | rating=GOOD/BAD, comment | creditChange | 学生（参与方） |

TaskCardVO 字段：id, taskNo, category, categoryName, title, points, publisherId, publisherNickname, publisherCredit, applicationCount, status, createdAt。

TaskDetailVO 额外字段：description, locationDesc, timeDesc, acceptorId, acceptorNickname, acceptedAt, deliveredAt, confirmDeadline, finishedAt, offline, offlineReason, conversationId, myApplicationStatus, canApply, canConfirm, canDeliver, canComplete, canCancel, canAbandon, canReview, myReviewDone, peerReviewDone, remainingSeconds。

### 4.3 聊天与通知

| 方法 | 路径 | 入参 | 返回 data 主要字段 | 权限 |
| --- | --- | --- | --- | --- |
| GET | /api/conversations | page, size | list[id, taskId, taskTitle, peerId, peerNickname, lastMessage, lastTime] | 学生 |
| GET | /api/conversations/{id}/messages | afterId（可选，增量拉取）, page, size | list[id, senderId, msgType, content, imagePath, createdAt] | 学生（会话参与方） |
| POST | /api/conversations/{id}/messages | msgType=text, content | message 对象 | 学生（会话参与方） |
| POST | /api/conversations/{id}/messages/image | multipart: file | message 对象（含 imagePath） | 学生（会话参与方） |
| GET | /api/notifications | page, size, onlyUnread（可选） | 分页 + list[id, type, title, content, relatedTaskId, isRead, createdAt] + unreadCount | 学生 |
| POST | /api/notifications/{id}/read | 无 | unreadCount | 学生 |
| GET | /api/dashboard/summary | 无 | points, credit, publishingCount, waitingCount, inProgressCount, pendingConfirmCount | 学生 |

### 4.4 积分与信用

| 方法 | 路径 | 入参 | 返回 data 主要字段 | 权限 |
| --- | --- | --- | --- | --- |
| GET | /api/points/logs | page, size, type（可选） | 分页 + list[createdAt, type, typeName, remark, changeAmount, balanceAfter] | 学生 |
| POST | /api/points/recharge | amountYuan（整数，≥1） | balance, addedPoints | 学生 |
| GET | /api/leaderboard | limit（默认 20，最大 50） | list[rank, userId, nickname, credit, completedCount, points], myRank | 学生 |
| GET | /api/credit/logs | page, size | 分页 + list[id, changeAmount, reason, reasonName, relatedTaskId, taskTitle, createdAt, appealId, appealStatus] | 学生 |
| POST | /api/appeals | multipart: taskId, content, file | appealId, status | 学生 |
| GET | /api/appeals/mine | page, size | 分页 + list[id, taskId, taskTitle, status, rejectReason, createdAt, handledAt] | 学生 |

### 4.5 看板统计

| 方法 | 路径 | 入参 | 返回 data 主要字段 | 权限 |
| --- | --- | --- | --- | --- |
| GET | /api/stats/personal | 无 | publishedCount, completedCount, acceptedCount, pointsIncome, pointsExpense | 学生 |
| GET | /api/stats/overview | 无 | publishTrend[7]{date,count}, categoryRatio[6]{category,categoryName,count,percent}, topAccepted[≤5]{taskId,title,category,categoryName,applicationCount,points} | 学生 |

### 4.6 管理端 admin

| 方法 | 路径 | 入参 | 返回 data 主要字段 | 权限 |
| --- | --- | --- | --- | --- |
| GET | /api/admin/appeals | status=PENDING/APPROVED/REJECTED, page, size | 分页 + list[id, userId, nickname, taskId, taskTitle, status, createdAt] | 管理员 |
| GET | /api/admin/appeals/counts | 无 | pending, approved, rejected | 管理员 |
| GET | /api/admin/appeals/{id} | 无 | id, nickname, credit, taskTitle, taskNo, content, screenshotPath, status, rejectReason, createdAt, handledAt, creditLog{changeAmount, reason, createdAt} | 管理员 |
| POST | /api/admin/appeals/{id}/approve | remark（可空） | status=APPROVED, credit | 管理员 |
| POST | /api/admin/appeals/{id}/reject | reason（必填） | status=REJECTED | 管理员 |
| GET | /api/admin/tasks | keyword, offline=0/1, page, size | 分页 + list[id, taskNo, title, category, categoryName, points, publisherNickname, status, offline, offlineReason, createdAt] | 管理员 |
| POST | /api/admin/tasks/{id}/offline | reason（必填） | offline=1 | 管理员 |
| POST | /api/admin/tasks/{id}/online | 无 | offline=0 | 管理员 |

### 4.7 静态文件

| 方法 | 路径 | 说明 | 权限 |
| --- | --- | --- | --- |
| GET | /uploads/{yyyy}/{MM}/{dd}/{file} | FileServlet 从 `E:\MyApp\uploads` 读文件返回，Content-Type 按扩展名，拒绝路径穿越 | 学生（图片地址带会话，同源） |

### 4.8 页面与接口对照

| 页面 | 调用的接口 |
| --- | --- |
| login.html | register、login、admin/login |
| dashboard.html | auth/me、dashboard/summary、stats/overview |
| lobby.html | tasks（列表）、tasks/{id}/apply、auth/me |
| task-detail.html | tasks/{id}、applications、apply、confirm、deliver、complete、cancel、abandon、reviews、conversations/{id}/messages、points/logs（仅展示余额刷新） |
| publish.html | auth/me（积分与信用）、tasks（POST） |
| my-tasks.html | tasks/mine、tasks/{id}/confirm、complete、cancel、abandon、reviews |
| messages.html | notifications、notifications/{id}/read、conversations、conversations/{id}/messages、messages/image |
| profile.html | auth/me、points/logs、points/recharge、credit/logs、appeals、stats/personal |
| leaderboard.html | leaderboard、auth/me |
| admin/appeals.html | admin/appeals/counts、admin/appeals、admin/appeals/{id}、approve、reject、auth/logout |
| admin/tasks.html | admin/tasks、offline、online、auth/logout |

---

## 5. 分阶段开发计划

### 阶段 1：环境与骨架

**目标**：把「Tomcat 能起来、静态页能打开、Java 接口能返回 JSON」这条链路先打通，后面所有工作都建在这上面。

**步骤**

1. 确认 D1、D2、D3 三个决策点，把结论写回本文第 1 节表格（改成「已定」并记录实际值）。
2. 建目录：`server\src\main\java\com\campusquest\{util,entity,vo,dao,service,filter,servlet}`、`server\src\main\webapp\{assets\css,assets\js\page,assets\lib,WEB-INF\lib,WEB-INF\classes}`、`uploads`、`backup`、`tools`。
3. 准备 jar：`mysql-connector-j-8.0.33.jar`、`gson-2.10.1.jar` 放入 `WEB-INF\lib`。servlet-api.jar 不复制，编译时从 Tomcat 的 `lib` 引用。
4. 准备前端库：`vue.min.js`(2.6.14)、`axios.min.js`(1.6.8)、`element-ui/index.js`(2.15.14)、`element-ui/theme-chalk/index.css` 及 `fonts` 目录（含 `element-icons.woff`、`element-icons.ttf`）放入 `assets\lib`。所有文件必须落在本地，页面里不允许出现 http 开头的外链。
5. 解压 Tomcat 9 到 `tools\tomcat9`；改 `conf\server.xml`：Connector 端口 8080、`URIEncoding="UTF-8"`、`maxPostSize` 保留默认（上传由 @MultipartConfig 控制）。
6. 新建 `conf\Catalina\localhost\campusquest.xml`：

```xml
<Context docBase="E:/MyApp/server/src/main/webapp" reloadable="true" />
```

7. 写 `build.ps1`：收集 `src\main\java` 下所有 `.java`，用 `javac -encoding UTF-8 -source 11 -target 11 -cp "tools\tomcat9\lib\servlet-api.jar;src\main\webapp\WEB-INF\lib\*" -d src\main\webapp\WEB-INF\classes` 编译。写 `start.ps1`（设 CATALINA_HOME 后调 startup.bat）、`stop.ps1`。
8. 写 `web.xml`（Servlet 3.1 版本头），先只注册一个 `PingServlet` 到 `/api/ping`，返回 `{"code":0,"message":"ok","data":{"time":"..."}}`。
9. 写一个只含标题的 `login.html`，确认静态资源可访问。

**产出物**：目录骨架、`build.ps1`、`start.ps1`、`stop.ps1`、`web.xml`、`PingServlet`、`login.html` 占位、`tools\tomcat9` 可用。

**完成判定**：执行 `build.ps1` 无报错；启动 Tomcat 后浏览器打开 `http://localhost:8080/campusquest/api/ping` 看到 JSON 且中文不乱码；打开 `/campusquest/login.html` 正常显示。

**预计耗时**：0.5 人天。

**自测清单**

- [ ] 8080 端口未被占用，`start.ps1` 后 `http://localhost:8080/` 出现 Tomcat 欢迎页
- [ ] `/campusquest/api/ping` 返回 code 0
- [ ] 改一行 Java 后重新 `build.ps1`，刷新接口能看到变化
- [ ] `assets\lib` 下 5 个库文件齐全，页面无外链
- [ ] 关闭 Tomcat 再启动，一切正常

---

### 阶段 2：数据库建表

**目标**：一次把 11 张表建好，灌入管理员账号，结构定死，后面不再改字段名。

**步骤**

1. 写 `server\sql\01_schema.sql`：建库 + 11 张表 + 全部索引，字段与第 3 节完全一致。表创建顺序 users → admins → tasks → applications → conversations → messages → reviews → point_logs → credit_logs → appeals → notifications。
2. 写 `server\sql\02_init.sql`：插入管理员 `admin`（password_hash 为 `admin123` 的 SHA-256 加盐值，与后端算法一致），并插入一条注释说明改密方式（直接改库）。
3. 写 `server\sql\03_seed.sql`（可选，仅验收用）：插 5 个学生账号（信用分别 100、98、95、89、84）、20 条任务覆盖六个分类与五种状态、若干申请与流水，方便阶段 9、12 看图表。演示数据用固定手机号 13800000001–13800000005。
4. 用 Navicat 或命令行执行三份 SQL：`mysql -u root -p < server\sql\01_schema.sql`。
5. 写 `DBUtil`（`MysqlDataSource` 单例 + `getConnection()` + `close` 静默），JDBC URL 见 2.5。

**产出物**：三份 SQL、执行成功的 `campus_quest` 库、`DBUtil.java`。

**完成判定**：`SHOW TABLES` 列出 11 张表；`SELECT * FROM admins` 有 1 条；用 `SHOW CREATE TABLE tasks` 检查字符集为 utf8mb4、索引齐全；`DBUtil` 写一个 main 方法能连上库并打印版本。

**预计耗时**：0.5 人天。

**自测清单**

- [ ] 11 张表全部存在，字段名、类型与第 3 节一致
- [ ] 唯一索引生效：重复插入同一 phone 报错；重复插入同一 (task_id, applicant_id) 报错
- [ ] 中文可写入：插一条标题含中文的任务能查出来
- [ ] 删除库后重跑 01、02 能完整恢复
- [ ] 管理员密码哈希与后端校验算法一致（先手算一条对比）

---

### 阶段 3：后端基础设施

**目标**：把响应格式、异常处理、Filter 链、DAO 基类、会话工具做完。之后每个模块只写业务。

**步骤**

1. `Resp.java`：`ok(data)`、`ok()`、`fail(code, message)`，内部用 Gson 序列化；`JsonUtil` 持有 Gson 单例（`setDateFormat("yyyy-MM-dd HH:mm:ss")`、关闭 HTML 转义），提供 `readBody(req, Class)`。
2. `BaseServlet extends HttpServlet`：重写 `service()`，按 `req.getMethod()` 与 `pathInfo` 分发到 `doGet/doPost/...` 的子方法；统一 try-catch，业务异常 `BizException(code,message)` 转 `fail`，其他异常记日志并返回 5000；统一设置响应头。
3. `PageResult<T>`：page、size、total、pages、list，`of(page,size,total,list)` 自动算 pages。
4. `BaseDao`：`getConnection()`、`closeQuietly()`、`begin/commit/rollback`、`queryOne/queryList` 的通用行映射接口（`RowMapper<T>` 自行实现），事务用「同一条 Connection 贯穿 Service」写法：Service 从 DBUtil 取连接、`setAutoCommit(false)`，把连接传给多个 DAO 方法。
5. `SessionUtil`：`loginStudent(req,user)`、`loginAdmin(req,admin)`、`currentUserId(req)`、`isAdmin(req)`、`logout(req)`。
6. Filter 链（`web.xml` 中按此顺序声明，顺序即执行顺序）：

| 顺序 | Filter | 映射 | 职责 |
| --- | --- | --- | --- |
| 1 | EncodingFilter | `/*` | `req.setCharacterEncoding("UTF-8")`，`resp.setCharacterEncoding("UTF-8")` |
| 2 | CorsFilter | `/*` | 同源部署下只补 `Access-Control-Allow-Origin`（取请求 Origin，不能用 `*` 配 credentials）、`Allow-Credentials: true`、允许方法与头；`OPTIONS` 直接 200 返回，不再往下走 |
| 3 | AutoSettleFilter | `/api/*` | 调用 `SettlementService.settleExpired()`（阶段 7 实现，先留空方法），异常吞掉不影响主流程 |
| 4 | AuthFilter | `/api/*` | 白名单放行；`/api/admin/*` 校验 ADMIN；其余校验 STUDENT；失败返回 1002（HTTP 401）或 1003（HTTP 403）的 JSON |

7. `web.xml` 加 404/500 错误页配置，避免 Tomcat 默认 HTML 错误页破坏 JSON 约定（在 Filter 与 BaseServlet 里已兜住，错误页只作为最后保险）。
8. `FileServlet` 映射 `/uploads/*`：把 pathInfo 拼到 `E:\MyApp\uploads` 下，`getCanonicalPath` 后校验前缀必须仍在根目录内，否则 404；按扩展名设 Content-Type；找不到返回 404。
9. `BizException` 与 `ErrorCode` 常量类，把第 2.3 节错误码写成常量。

**产出物**：`Resp`、`JsonUtil`、`BaseServlet`、`PageResult`、`BaseDao`、`SessionUtil`、`BizException`、`ErrorCode`、四个 Filter、`FileServlet`、完整 `web.xml`。

**完成判定**：`GET /api/tasks` 未登录返回 HTTP 401 + `{"code":1002,...}`；`GET /api/ping` 正常；`OPTIONS /api/tasks` 返回 200 且带 CORS 头；`GET /uploads/不存在.jpg` 返回 404；故意让一个 Servlet 抛异常，返回 5000 而不是 Tomcat 报错页。

**预计耗时**：1 人天。

**自测清单**

- [ ] 未登录访问任一受保护接口都是 401 + code 1002，响应体是 JSON
- [ ] 响应中文不乱码（包括错误 message）
- [ ] OPTIONS 预检不会被 AuthFilter 拦成 401
- [ ] `FileServlet` 传 `../../etc/passwd` 返回 404
- [ ] 抛出的 RuntimeException 不会把堆栈返回给前端
- [ ] `build.ps1` 仍能一次编译通过

---

### 阶段 4：账号模块

**目标**：注册、登录、退出、当前用户信息可用；login.html 做完；顶栏数据接真实接口；未登录拦截生效。

**步骤**

1. `User`、`Admin` 实体；`UserDao`：`findByPhone`、`insert`、`findById`、`updatePoints`、`updateCredit`、`updateAvatar`；`AdminDao`：`findByUsername`。
2. `TextUtil`：`sha256Hex(s)`、`isPhone(s)`（`^\d{11}$`）、`maskPhone(s)`（138****1234）。
3. `AuthService.register(phone, nickname, password)`：校验 → 查重（2001）→ 事务内插 users + 插 `point_logs`（REGISTER +100，balance_after=100）+ 插 `credit_logs`（INIT +100）→ 返回 userId。
4. `AuthService.login`：查用户 → 校验密码（2003）→ 写 Session → 返回 LoginVO。
5. `AuthService.adminLogin`：同上走 admins 表，错误码 2004。
6. `AuthServlet` 挂在 `/api/auth/*`，按 pathInfo 分发 register/login/admin/login/logout/me。`me` 返回 phoneMasked 与 `unreadCount`（阶段 9 前先固定 0）。
7. 前端 `core.js`：

```js
const api = axios.create({ baseURL: '/campusquest/api', withCredentials: true, timeout: 15000 });
api.interceptors.response.use(r => {
  const b = r.data;
  if (b.code !== 0) { ElementUI.Message.error(b.message); return Promise.reject(b); }
  return b.data;
}, err => {
  if (err.response && err.response.status === 401) location.href = '/campusquest/login.html';
  else ElementUI.Message.error('网络异常，请稍后再试');
  return Promise.reject(err);
});
```

同时加 `CQ.dict`（分类、状态、类型映射）、`CQ.fmtTime`、`CQ.money`。

8. `components.js`：用 `<script type="text/x-template" id="tpl-topbar">` 定义顶栏组件（平台名、积分、消息红点、头像），侧栏组件（7 项，收当前页参数高亮）；每个学生页 include 这两个模板并用 `Vue.component` 注册。
9. `login.html`：ElementUI `el-tabs` 两个 tab（登录 / 注册），登录 tab 手机号 + 密码，注册 tab 手机号 + 昵称 + 密码 + 确认密码；前端做 11 位数字校验；成功后按返回 role 跳 `dashboard.html` 或 `admin/appeals.html`。切换 tab 清空表单。页面底部加一行「管理员登录」链接或第三个 tab 仅给 admin 用（两 tab 内再放一个「管理员入口」切换块，避免破坏 PRD 两 tab 结构）。
10. 所有学生页接入顶栏组件，`/api/auth/me` 初始化积分与未读数。

**产出物**：账号相关实体、DAO、Service、Servlet；`core.js`；`components.js` 顶栏与侧栏；完整 `login.html`。

**完成判定**：注册新号后库里多一条 users + 两条流水；重复注册报 2001；密码错报 2003；退出后刷新 dashboard 被踢回 login；登录后顶栏积分显示 100。

**预计耗时**：1.5 人天。

**自测清单**

- [ ] 11 位手机号注册成功，流水有 REGISTER +100、信用有 INIT +100
- [ ] 10 位或含字母手机号被拒，提示「请输入 11 位手机号」
- [ ] 重复手机号注册提示已注册
- [ ] 登录成功后 role 分流正确，管理员进 admin/appeals.html
- [ ] 未登录直接访问 dashboard.html 被重定向到 login.html
- [ ] 顶栏积分与库里一致，红点在 0 条时不显示
- [ ] 密码错误 3 次都返回中文提示，不泄露账号是否存在

---

### 阶段 5：任务发布与大厅

**目标**：能发布任务（扣积分）、大厅能筛选排序搜索分页、详情页能看全信息。此阶段先不做申请与后续流转。

**步骤**

1. `Task` 实体 + `TaskDao`：`insert`、`findById`、`findCards(条件, page, size)`（连 users 取昵称与信用、连 applications 取申请数，用子查询 `(SELECT COUNT(*) FROM applications a WHERE a.task_id = t.id)` 避免 group by 分页错乱）、`countCards(条件)`、`listMine`、`offline/online`。
2. `task_no` 生成：`CQ-` + `yyyyMMdd` + `-` + 三位序号，取当天最大序号 +1；插入冲突（唯一索引）时重试最多 3 次。
3. `TaskService.publish`：

```text
事务开始
SELECT points, credit FROM users WHERE id=? FOR UPDATE
if (credit < 90) -> 3002
if (points < 预算) -> 3001
UPDATE users SET points = points - ? WHERE id = ? AND points >= ?   -- 影响行数必须为 1
INSERT tasks(status=WAITING)
INSERT point_logs(type=PUBLISH, change_amount=-预算, balance_after=新余额, remark=标题)
提交
```

积分不足时整笔回滚，不产生任务。

4. `PointService` 先提供最小方法：`changePoints(conn, userId, delta, type, taskId, remark)`，返回新余额并写流水；后续阶段复用。
5. `TaskServlet`：`GET /api/tasks`（参数校验：page≥1、size≤50、sort 只接受三个值、category 只接受六个码）、`POST /api/tasks`、`GET /api/tasks/{id}`。
6. 列表默认只查 `status='WAITING' AND offline=0`；`onlyWaiting` 参数保留但默认 true，取消勾选时为 false 时仍只查未下架任务并按状态筛（PRD 要求「只看待接单」开关，因此默认展示除已完成/已取消外的全部进行中类任务：实现为 `onlyWaiting=true` 时 `status='WAITING'`，false 时 `status IN ('WAITING','IN_PROGRESS','PENDING_CONFIRM')`）。
7. 详情 VO 里算 `canApply`（登录者非发布者、status=WAITING、信用≥85、无 PENDING/SELECTED 申请）、`canCancel`（发布者且 WAITING）、其余 can* 先置 false。
8. 前端 `publish.html`：六个字段用 ElementUI 表单，积分 `el-input-number`（1–999）、分类 `el-select`；进页面先调 `auth/me`，信用<90 时整页替换为拦截提示 + 「去个人中心」按钮；余额不足在前端先提示，提交仍以后端为准。发布成功跳 `my-tasks.html`。
9. `lobby.html`：分类 chips（全部 + 六类）、关键词输入 + 搜索按钮、排序 `el-select`（最新发布 / 积分从高到低 / 积分从低到高）、「只看待接单」`el-switch`、`el-pagination`。卡片用 `task-card` 组件（`x-template`），CSS Grid：默认 `grid-template-columns: repeat(3, 1fr)`，`@media (min-width: 1600px)` 改 `repeat(4, 1fr)`；标题 `-webkit-line-clamp: 2`；右下角「N 人申请」角标。
10. `task-detail.html`：左列任务信息 + 发布者卡 + 申请记录列表；右列先放状态标签与只读信息，操作按钮留给阶段 6；未登录跳登录。

**产出物**：Task 相关 DAO/Service/Servlet、`task-card` 组件、`publish.html`、`lobby.html`、`task-detail.html` 只读版。

**完成判定**：发布 20 积分任务后余额减 20、流水新增、大厅第一页出现该卡片；分类筛选、搜索、排序、分页都生效；1280px 三列、1600px 四列；卡片五项信息齐全。

**预计耗时**：2 人天。

**自测清单**

- [ ] 发布后积分、流水、任务三处一致
- [ ] 余额 10 发布 20 积分任务被拦，库中无新任务
- [ ] 信用 89 账号进发布页被拦，提示「信用分低于 90，暂不能发布任务」
- [ ] 分类筛选只出该类；「全部」恢复
- [ ] 关键词命中标题或描述，无结果显示空状态
- [ ] 积分排序方向正确，两种顺序结果互为逆序
- [ ] 第 13 条任务出现分页，第 2 页无重复
- [ ] 已下架任务（手工改 offline=1）不出现在大厅
- [ ] 卡片标题超长被截两行，不撑破布局

---

### 阶段 6：接单流转

**目标**：申请、确认、送达、取消、放弃、我的任务全部打通；聊天会话在确认时自动创建（聊天界面阶段 9 做）。

**步骤**

1. `Application` 实体 + `ApplicationDao`：`insert`、`findByTaskAndUser`、`listByTask`、`updateStatus`、`selectOne`（把同任务其他 PENDING 改 REJECTED）、`countByTask`、`findSelected`。
2. `ApplicationService.apply`：校验任务存在且 WAITING（3004/3007）、非本人（3005）、信用≥85（3003）、未申请（3006）；若已有 ABANDONED 记录则复用行改回 PENDING；通知发布者（APPLICATION_RECEIVED）。
3. `ApplicationService.confirm(taskId, applicationId, publisherId)`：`SELECT ... FOR UPDATE` 锁任务 → 校验发布者本人、状态 WAITING、申请属于该任务且 PENDING → `UPDATE tasks SET status='IN_PROGRESS', acceptor_id=?, accepted_at=NOW()` → 选中该申请、其余改 REJECTED → `INSERT conversations`（task_id 唯一，若已存在则更新 acceptor_id）→ 通知申请人（APPLICATION_CONFIRMED）→ 提交。
4. `TaskService.cancel`：发布者 + WAITING → 状态 CANCELLED → 退款（REFUND 流水，balance_after）→ 提交。
5. `TaskService.deliver`：接单者 + IN_PROGRESS → `delivered_at=NOW(), confirm_deadline=DATE_ADD(NOW(), INTERVAL 24 HOUR), status='PENDING_CONFIRM'`。
6. `TaskService.abandon`：接单者 + IN_PROGRESS → 算出 `acceptPastMinutes = TIMESTAMPDIFF(MINUTE, accepted_at, NOW())`；`>30` 则 `UPDATE users SET credit=GREATEST(credit-2,0)` + credit_logs(ABANDON) + `penalty_applied=1`；任务回 WAITING、acceptor_id 置空、申请 ABANDONED、`abandoned_at=NOW()`；会话保留。
7. `MyTaskVO` 与 `GET /api/tasks/mine`：published 查 publisher_id，accepted 查 acceptor_id，附对方昵称、申请数、剩余自动确认秒数、可执行动作标记。
8. 前端 `task-detail.html` 补齐操作区：按 `can*` 显示「申请接单 / 确认 TA 接单 / 已送达 / 确认完成交付 / 取消任务 / 申请放弃」；`el-dialog` 二次确认；申请记录区在发布者视角每行给「确认 TA」按钮；倒计时用 `remainingSeconds` 每秒本地递减。
9. `my-tasks.html`：两个 tab 接 `/api/tasks/mine`，tab 上显示条数，按状态筛选 chips，行内按钮按状态显示；待确认完成行显示剩余时间。
10. 大厅卡片和详情页的「N 人申请」改为真实计数。

**产出物**：applications 全链路、mine 接口、`task-detail.html` 交互版、`my-tasks.html`。

**完成判定**：A 发布、B 申请、A 确认后任务变进行中且 B 的「我接的」出现该任务、其余申请变未选中、库中自动出现 conversation 记录；B 送达后 A 的工作台「待我确认」+1。

**预计耗时**：2 人天。

**自测清单**

- [ ] 自己不能申请自己的任务（按钮不可用，接口也拒绝）
- [ ] 同一账号重复申请提示已申请
- [ ] 已确认后再申请提示已被接单
- [ ] 确认后其他申请状态变「未选中」，不进入他们的「我接的」
- [ ] 取消任务后积分全额退回，任务从大厅消失
- [ ] 30 分钟内放弃：回大厅、信用不变、无扣分记录
- [ ] 超过 30 分钟放弃：回大厅、信用 −2、扣分记录 +1
- [ ] 同一任务反复放弃不会重复扣分（penalty_applied 生效）
- [ ] 我的任务两个 tab 条数与实际一致

---

### 阶段 7：结算与评价

**目标**：完成结算、24 小时自动确认、双方互评、信用加减分落地。

**步骤**

1. `SettlementService.complete(taskId, operatorUserId, auto)`：事务内 `UPDATE tasks SET status='COMPLETED', finished_at=NOW() WHERE id=? AND status='PENDING_CONFIRM'`，影响行数 0 直接返回（幂等）→ 给 acceptor 加预算（`PointService.changePoints`，type=INCOME，remark=任务标题）→ 通知双方（SETTLEMENT，自动时正文带「系统自动确认」）→ 提交。
2. `SettlementService.settleExpired()`：`SELECT id FROM tasks WHERE status='PENDING_CONFIRM' AND confirm_deadline <= NOW() LIMIT 50`，逐条调用 `complete(id, null, true)`；单条失败只记日志不中断。整体加 `synchronized` + 3 秒节流（`lastRunAt`），由 AutoSettleFilter 在每次 `/api/*` 请求前触发。
3. 前端「惰性触发」兜底：`task-detail.html` 与 `my-tasks.html` 在存在 PENDING_CONFIRM 任务时每 30 秒轮询一次，倒计时归零后自动刷新状态；「工作台」每次进入都请求新数据。
4. `Review` 实体 + `ReviewDao`；`TaskService.review`：校验任务 COMPLETED、操作者是发布者或接单者、被评价人是对方、本任务未评过（3009）；写 reviews；GOOD 给被评价人 +3（`credit = LEAST(credit+3, 100)`，已是 100 时仍写一条 change_amount=0 且 remark=「已封顶」的 credit_logs）、BAD −3（`GREATEST(credit-3,0)`）；通知被评价人（REVIEW_RECEIVED）。
5. 详情页与我的任务页在已完成任务上显示「评价 TA」按钮，弹窗（`el-dialog` + 好评/差评单选 + 文字说明，文字最多 100 字），评完按钮改为「已评价」。
6. `point_logs` 与 `credit_logs` 的写入全部走 `PointService` / `CreditService`，禁止在 Servlet 里直接拼 SQL。

**产出物**：`SettlementService`、`ReviewDao`、评价接口、评价弹窗组件。

**完成判定**：确认完成后接单者积分增加、双方收到结算通知；把 `confirm_deadline` 手工改成过去时间再刷新任意页面，任务自动变已完成且只结算一次；好评后对方信用 +3。

**预计耗时**：2 人天。

**自测清单**

- [ ] 发布者点确认后积分只加一次，重复点无副作用
- [ ] 手工把 deadline 改成过去时间，刷新页面任务自动完成并写入通知
- [ ] 连续快速刷新 10 次，不出现重复结算或余额虚增
- [ ] 100 分用户收到好评仍为 100，且流水有一条「已封顶」
- [ ] 同一任务同一人不能评价两次
- [ ] 非参与方调用评价接口被拒（1003）
- [ ] 未完成任务评价被拒（3010）

---

### 阶段 8：积分与信用

**目标**：个人中心做完整：积分流水、模拟充值、排行榜、信用规则与扣分记录、申诉提交。

**步骤**

1. `PointLogDao`：`listByUser(page,size,type)` + `count`；`PointServlet` 提供 `GET /api/points/logs`、`POST /api/points/recharge`（金额整数 1–10000 元，换算 ×10，写 RECHARGE 流水）。
2. `leaderboard`：`SELECT id,nickname,credit,points,(SELECT COUNT(*) FROM tasks t WHERE t.acceptor_id=u.id AND t.status='COMPLETED') completed FROM users u ORDER BY points DESC, completed DESC, id ASC LIMIT ?`；我的名次用 `COUNT(*)+1` 计算（分数高于我的人数 +1）。排名按当前积分余额实时计算，不引入「每周更新」「累计获得」等未确认口径。前端删除线框图里「每周一 00:00 更新」的文案。
3. `CreditLogDao`：`listByUser`，联表 tasks 取标题，联 appeals 取申诉状态；`GET /api/credit/logs`。
4. `AppealService.submit(userId, taskId, content, file)`：校验该 task 的 ABANDON 扣分记录存在且 credit_logs 有对应（否则 1001）、未申诉过（3011）、content 非空、截图必传；保存图片到 uploads（复用阶段 9 的 `FileUtil.saveImage`，本阶段先写该方法）；插 appeals(status=PENDING)。
5. `GET /api/appeals/mine`：我的申诉列表（状态、驳回理由、处理时间）。
6. 前端 `profile.html`：资料卡（昵称、脱敏手机号、注册时间；删除线框里的「已实名认证」「校园身份」）、积分类（余额 + 模拟充值弹窗）、信用卡（大数字 + 规则列表，规则文案与 PRD 一致：好评 +3、差评 −3、超 30 分钟放弃 −2、90 分以下不能发布、85 分以下不能接单、申诉通过退回 2 分）、积分流水表（分页）、扣分记录表（每条带「发起申诉」按钮，已申诉显示状态）、申诉弹窗（说明 + 上传截图 + 提交）。
7. `leaderboard.html`：接 `/api/leaderboard`，前 3 名高亮，我的名次单独一张卡；列：名次、昵称、信用分、完成任务、积分。
8. 信用拦截补全：`AuthService.me` 与 `TaskService.publish` 的 3002/3003 提示文案前后端一致。

**产出物**：积分流水页、充值、排行榜、信用流水、申诉提交、profile 完整页。

**完成判定**：充值 10 元积分 +100 且流水一条；排行榜名次与手算一致；扣分记录可点申诉并生成 PENDING 记录；信用 84 账号申请按钮提示 3003。

**预计耗时**：1.5 人天。

**自测清单**

- [ ] 充值 10 元 → +100 积分，流水余额与卡片一致
- [ ] 充值 0 元或负数被拒
- [ ] 流水分页正确，倒序排列
- [ ] 排行榜前 3 高亮，我的名次与总分顺序一致
- [ ] 信用规则文案与 PRD 完全一致，没有「+2/−2」旧文案
- [ ] 同一扣分第二次申诉被拒（3011）
- [ ] 不上传截图提交申诉被拒
- [ ] 个人中心没有「已实名认证」「校园身份」字样

---

### 阶段 9：消息与聊天图片

**目标**：五类系统通知、未读数、任务内一对一聊天、图片上传与展示。

**步骤**

1. `NotificationDao`：`insert`、`listByUser(page,size,onlyUnread)`、`countUnread`、`markRead(id,userId)`；`NotificationService.notify(userId, type, title, content, taskId)` 供各 Service 调用（阶段 6、7 先把调用点埋好，本阶段把通知表真正写入）。
2. 把阶段 6、7 里的通知调用逐个补齐：收到申请、被确认、收到评价、结算到账、申诉结果（申诉结果在阶段 11 由管理端写入）。
3. `FileUtil.saveImage(Part part)`：校验大小 ≤5MB（4002）、扩展名白名单 jpg/jpeg/png/gif/webp（4001）、文件名 UUID + 原扩展名；目录 `E:\MyApp\uploads\yyyy\MM\dd\`，不存在则创建；返回相对路径 `uploads/yyyy/MM/dd/xxx.jpg`。上传根目录写到 `FileUtil` 常量，和 `FileServlet` 共用。
4. `ConversationDao`、`MessageDao`；`ChatService`：`listMyConversations`（联 tasks 取标题、按最后消息时间倒序）、`listMessages(convId, afterId, page, size)`、`sendText`、`sendImage`；每个方法先校验操作者是会话的 publisher_id 或 acceptor_id，否则 1003。
5. `ConversationServlet`、`NotificationServlet` 挂好路由；图片接口标注 `@MultipartConfig(maxFileSize = 5*1024*1024, maxRequestSize = 6*1024*1024)`。
6. 前端 `messages.html`：两个 tab（系统通知 / 聊天会话）。通知列表：未读加粗、点击调 read 并跳关联任务；聊天列表：点进会话显示消息流（自己的气泡靠右），底部输入框 + 发图按钮（`el-upload` 隐藏列表，选完即上传），新消息每 5 秒轮询增量（`afterId=最后一条id`）。
7. `task-detail.html` 右侧聊天区接同一套接口，消息与 messages.html 数据一致。
8. 顶栏红点接 `/api/auth/me` 的 unreadCount，进入消息中心并读完后刷新。

**产出物**：通知与聊天 DAO/Service/Servlet、图片上传与读取、`messages.html`、详情页聊天区。

**完成判定**：B 申请后 A 收到通知且红点变 1；A 确认后 B 收到通知；聊天发文字和图片都即时显示，`E:\MyApp\uploads\2026\11\09` 下出现新文件，库中只有相对路径。

**预计耗时**：2 人天。

**自测清单**

- [ ] 五类通知都能触发（申诉结果在阶段 11 验）
- [ ] 读取通知后红点数字减少，刷新后保持
- [ ] 非会话参与方调用消息接口返回 1003
- [ ] 发 6MB 图片被拒并提示不超过 5MB
- [ ] 上传 .exe 改名为 .jpg 仍被扩展名白名单拦下
- [ ] 图片在聊天区显示缩略图，点击可看原图
- [ ] 中文文件名上传后，服务器文件名是 UUID，不出现乱码
- [ ] 重启 Tomcat 后历史图片仍能打开

---

### 阶段 10：看板统计

**目标**：工作台四张摘要卡、三块图表、个人统计全部接真实数据。

**步骤**

1. `StatDao` + `StatService`：
   - 近 7 天：`SELECT DATE(created_at) d, COUNT(*) c FROM tasks WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) GROUP BY DATE(created_at)`，Java 侧补齐缺失日期为 0，输出 7 条。
   - 六分类占比：`SELECT category, COUNT(*) c FROM tasks WHERE offline=0 GROUP BY category`，补齐 0 值分类，percent 用 `Math.round(c*100.0/total)`。
   - Top5：`SELECT t.id, t.title, t.category, t.points, COUNT(a.id) c FROM tasks t JOIN applications a ON a.task_id=t.id WHERE t.offline=0 GROUP BY t.id ORDER BY c DESC, t.id ASC LIMIT 5`。
   - 个人统计：发布数（不含已取消、已下架）、完成数、接单数（只算已完成）、积分收入合计、支出合计。
2. `GET /api/stats/overview`、`GET /api/stats/personal`、`GET /api/dashboard/summary` 三个接口落地；summary 的「待我确认」= 我发布且 PENDING_CONFIRM 的数量，「我发布中」= WAITING + IN_PROGRESS。
3. 前端折线图：不引第三方图表库（避免额外依赖），用 SVG 手写 7 点折线 + 圆点 + 数值标签 + 日期横轴，封装为 `line-chart` 组件，入参 `[{date,count}]`，自动算 Y 轴上限（最大值向上取整到 5 的倍数，全 0 时上限 5）。
4. 饼图：用 `conic-gradient` 按六个百分比拼环，中心显示任务总数；图例列分类名 + 百分比；总数为 0 显示「暂无任务数据」。
5. Top5 列表按接口渲染，含名次、标题、分类标签、申请人数与积分；不足 5 条有几条显示几条。
6. `dashboard.html` 四张摘要卡与顶部问候语接 `summary`；信用卡底部文案按分段取（100 / 90–99 / 85–89 / 0–84）。
7. `profile.html` 顶部「已完成 N 个任务」与个人统计区接 `stats/personal`。

**产出物**：统计接口、`line-chart` 组件、饼图、工作台完整页。

**完成判定**：当天发布 1 条任务后折线最后一天 +1；六个百分比合计 100；Top5 按申请人数降序且不重复；个人四项统计与库中手查一致。

**预计耗时**：1.5 人天。

**自测清单**

- [ ] 折线图 7 个点日期连续，无数据日为 0
- [ ] 全站 0 任务时饼图显示空状态，不报错
- [ ] 百分比合计为 100（四舍五入后手工核对）
- [ ] Top5 中同一任务只出现一次
- [ ] 新建任务后刷新工作台数字立即变化
- [ ] 工作台没有固定写死的数字
- [ ] 信用 89 的账号摘要卡文案为「信用偏低，暂不能发布任务」

---

### 阶段 11：管理端

**目标**：管理员登录后能处理申诉、能下架与恢复任务。

**步骤**

1. `AdminAppealServlet`：`GET /api/admin/appeals`（按 status 筛选 + 分页）、`GET /counts`、`GET /{id}`（含申诉人昵称与当前信用、任务标题与编号、截图路径、对应信用流水）。
2. `approve`：事务内校验 appeals.status=PENDING（否则 3012）→ 状态改 APPROVED、handled_by、handled_at → `credit + 2`（不超过 100）+ credit_logs(APPEAL_REFUND, +2) → 通知申诉人（APPEAL_RESULT，正文含「申诉已通过，信用分已退回 2 分」）。
3. `reject`：reason 必填（空则 1001）→ 状态 REJECTED、reject_reason、handled_by、handled_at → 通知申诉人（APPEAL_RESULT，正文含驳回理由）。
4. `AdminTaskServlet`：`GET /api/admin/tasks`（keyword 匹配 title / task_no / 发布者昵称，offline 过滤，分页）、`offline`（reason 必填）、`online`。下架不改 status，只改 offline 三个字段；已下架任务在大厅、详情、Top5、饼图统计中都不出现（饼图与 Top5 已带 offline=0 条件，详情需补 1004 或「已下架」提示）。
5. 前端 `admin/appeals.html`：深色顶栏 + 三项侧栏；左侧申诉列表（三档筛选 + 数量角标 + 选中态），右侧详情（申诉人、任务、信用流水、说明、截图 `el-image` 可点开大图），底部「通过」「驳回」两个按钮，驳回弹窗必须填理由。处理后列表与计数立即刷新。
6. 前端 `admin/tasks.html`：搜索框 + 状态筛选（全部 / 正常 / 已下架）+ 表格（编号、标题、分类、积分、发布者、状态、下架原因、操作），下架/恢复都要二次确认，下架需填理由。
7. 退出登录走 `POST /api/auth/logout` 后跳 login.html。

**产出物**：管理端两个 Servlet、两个页面。

**完成判定**：申诉通过后学生信用 +2 并收到通知；驳回不填理由无法提交；下架任务从大厅消失，恢复后回来且原状态不变。

**预计耗时**：1.5 人天。

**自测清单**

- [ ] 学生访问 `/api/admin/appeals` 返回 1003
- [ ] 已处理申诉再点通过返回 3012，信用不重复加
- [ ] 通过后 credit_logs 新增 APPEAL_REFUND +2，学生收到通知
- [ ] 驳回理由为空时前端拦住，绕过前端直接调接口也返回 1001
- [ ] 下架后大厅、详情、Top5 都看不到该任务
- [ ] 恢复后任务回到大厅，原状态（如进行中）不变
- [ ] 管理端侧栏只有申诉处理、任务管理、退出三项

---

### 阶段 12：联调与验收

**目标**：按 PRD 第 10 节的 39 条验收标准逐条跑通，修掉联调问题，交付可演示版本。

**步骤**

1. 造数：用 `03_seed.sql` 准备三个账号 A（信用 100）、B（信用 89）、C（信用 84），各发布 2–3 条任务。
2. 按第 7 节的验收映射逐条执行，记录「通过 / 不通过 / 说明」，不通过的回到对应阶段修。
3. 重点回归四件事：状态机非法操作、积分账实相符（余额 = 流水累加）、信用 0–100 边界、24 小时自动确认幂等。
4. 断网验收：关掉网络，刷新全部 11 个页面，检查开发者工具 Network 中无外网请求，字体图标正常。
5. 重启验收：关浏览器、重启 Tomcat、重启电脑后，数据与图片都在。
6. 备份恢复演练一次：mysqldump + 复制 uploads → 删库 → 导入 + 还图片 → 抽查三处。
7. 清理演示数据（保留管理员与 1 个演示账号），写一页启动说明（放 `server\README.txt`，只写启动与备份两条命令，不写新功能）。

**产出物**：验收记录表、修复后的代码、`server\README.txt`。

**完成判定**：PRD 第 10 节 39 条全部通过；断网可用；重启不丢数据；备份能恢复。

**预计耗时**：2 人天。

**自测清单**

- [ ] 39 条验收逐条有结论，无「大概可以」
- [ ] 余额与流水累加始终相等
- [ ] 非法状态操作全部被 3004 拦住
- [ ] 所有页面无 404 链接，未实现入口是 `href="#"`
- [ ] 断网下 11 个页面样式与图表正常
- [ ] 重启后聊天图片可打开
- [ ] 备份恢复后任务数、积分、图片三处一致

---

## 6. 已知风险与坑

### 6.1 本机无 Maven

- 不引入 Maven，依赖只有两个后端 jar（mysql-connector-j、gson），直接放 `WEB-INF\lib`。
- 编译用 `javac` 脚本，classpath 用 `tools\tomcat9\lib\servlet-api.jar` + `WEB-INF\lib\*`；不要用 `-cp` 指向 `jre\lib\ext`。
- 编译输出必须落在 `WEB-INF\classes`，否则 Tomcat 找不到类。
- 每次改完 Java 都要重新跑 `build.ps1`，再重启 Tomcat（`reloadable="true"` 只对 class 变化部分生效，稳妥起见重启）。
- 坑：PowerShell 里 `-cp` 后面带 `*` 要加引号，否则被当通配符展开。

### 6.2 本机无 Tomcat

- 用免安装 zip，必须是 9.x（javax.servlet）。Tomcat 10/11 改成 jakarta.servlet，所有 `import javax.servlet.*` 都会编译失败。
- 通过 `conf\Catalina\localhost\campusquest.xml` 指向 `E:/MyApp/server/src/main/webapp`，路径用正斜杠，改代码不用重新部署。
- 启动前确认 8080 未被占用：`netstat -ano | findstr :8080`，占用则改 `server.xml` 端口或停掉占用进程。
- Tomcat 进程关不掉时用 `shutdown.bat`，仍不行再按 PID 结束 java 进程。
- 坑：Windows 路径反斜杠在 XML 里可能被转义，统一写正斜杠。

### 6.3 Vue2 不用脚手架怎么组织

- 不使用 `.vue` 单文件组件，所有组件用 `<script type="text/x-template" id="tpl-xxx">` 写模板，`Vue.component('task-card', { template: '#tpl-task-card', props: [...] })` 注册。
- 模板必须写在 Vue 挂载点之外（放在 body 末尾），否则浏览器会把它当普通 HTML 渲染出来。
- 每个页面一个 html + 一个 `assets/js/page/xxx.js`，公共逻辑只在 `core.js`（axios 封装、字典、格式化）和 `components.js`（顶栏、侧栏、任务卡片、状态标签、分页、空状态、错误条、评价弹窗）。
- 载入顺序固定：vue → element-ui → axios → core.js → components.js → 页面脚本。ElementUI 依赖 Vue 全局变量，顺序错了页面白屏。
- 接口统一走 `core.js` 里的 `api` 实例，页面里不写 `axios.get('/api/...')`；拦截器负责弹错、401 跳登录、解包 `data`。
- 坑：ElementUI 的 `el-date-picker` 等组件赋值要用 `$set`；`v-model` 绑到 `data` 里未声明的字段不会触发更新，data 先声明全字段。
- 坑：`el-table` 的列模板用 `slot-scope`，Vue2 语法，不要照抄 Vue3 的 `#default`。

### 6.4 24 小时自动确认在本地怎么实现

本地没有可靠的定时任务宿主，采用「访问时惰性结算」，具体做法：

1. `AutoSettleFilter` 映射 `/api/*`，每次请求进入时调 `SettlementService.settleExpired()`，先判断距离上次执行是否超过 3 秒，未超过直接跳过（避免每个请求都扫表）。
2. `settleExpired()` 查 `status='PENDING_CONFIRM' AND confirm_deadline <= NOW()` 最多 50 条，逐条走与「发布者手动确认」完全相同的 `complete()`。
3. `complete()` 用条件更新做幂等闸门：`UPDATE tasks SET status='COMPLETED'... WHERE id=? AND status='PENDING_CONFIRM'`，影响行数为 0 说明已被别人结算，直接返回。
4. 时间基准全部用数据库 `NOW()`，不用应用服务器时间，避免时区偏差。
5. 页面侧兜底：`task-detail.html`、`my-tasks.html` 存在待确认任务时每 30 秒轮询一次；倒计时归零后触发刷新，用户看到的是已完成并已结算。
6. 说明清楚这个取舍：如果没有任何人访问，任务会在下一次访问时结算，结算时间可能晚于 24 小时，但金额与状态结果一致。第一版接受这个行为，不额外起后台线程。

### 6.5 跨域与 Filter 顺序

- 同源部署（页面与接口都在 `/campusquest` 下），正常不触发跨域；CorsFilter 仅为将来分端口调试预留。
- 顺序必须是 编码 → CORS → 自动结算 → 鉴权，写错会导致：OPTIONS 预检被鉴权拦成 401、中文参数在鉴权前就乱码、自动结算在未登录请求上不执行。
- CORS 带 credentials 时 `Access-Control-Allow-Origin` 不能是 `*`，必须回显请求的 Origin。
- `OPTIONS` 请求要在 CorsFilter 里直接返回 200 并结束，否则浏览器预检失败。

### 6.6 中文编码

需要在四个位置同时设置，缺一处就会乱码：

1. `web.xml` 里 EncodingFilter 调 `request.setCharacterEncoding("UTF-8")`（必须在读取任何参数之前）。
2. 响应统一 `response.setContentType("application/json;charset=UTF-8")`。
3. Tomcat Connector 加 `URIEncoding="UTF-8"`（GET 参数走 URL，不设会按 ISO-8859-1 解）。
4. 数据库库表 utf8mb4 + JDBC URL `useUnicode=true&characterEncoding=utf8`。
5. 另外 `javac` 加 `-encoding UTF-8`，否则源码里的中文常量会变成乱码。

### 6.7 图片上传大小与类型限制

- 后端 `@MultipartConfig(maxFileSize = 5MB, maxRequestSize = 6MB)`，超限统一转 4002。
- 扩展名白名单 jpg/jpeg/png/gif/webp，不做白名单外放行；同时校验 `Content-Type` 以 `image/` 开头，双保险。
- 文件不落 webapp 内，落 `E:\MyApp\uploads`；读取只通过 `FileServlet`，`getCanonicalPath()` 后必须仍在 uploads 根目录内，防目录穿越。
- 文件名用 UUID，绝不使用用户原始文件名拼路径；原扩展名只取最后一段并转小写。
- 数据库只存相对路径，不存绝对路径，换机器时只需改 uploads 根常量。
- 坑：Tomcat 的 `maxSwallowSize` 可能导致超限后连接异常，前端要处理错误提示；`el-upload` 需 `:auto-upload="false"` 配合手动提交或自定义 `http-request`，避免默认上传路径打错。

### 6.8 积分并发扣减

- 扣减统一用条件更新：`UPDATE users SET points = points - ? WHERE id = ? AND points >= ?`，影响行数为 0 即余额不足，抛 3001 回滚。
- 所有涉及积分的写入必须在同一事务内完成「改余额 + 写流水」，`balance_after` 用更新后的值，不允许先算后写。
- 结算给接单者用 `UPDATE users SET points = points + ?`，配合任务的幂等条件更新，避免重复加分。
- 同一用户的发布操作加 `SELECT ... FOR UPDATE` 行锁，防止两个请求同时读到旧余额。
- `point_logs` 只追加不修改，出问题以流水为准对账。

### 6.9 其他容易踩的点

| 风险 | 处理办法 |
| --- | --- |
| 同一天 task_no 并发重复 | 唯一索引 + 取当天最大值 + 冲突重试 3 次，仍失败返回 5000 |
| Session 存内存，重启丢失 | 属预期；前端 401 统一跳登录，页面不白屏 |
| 「N 人申请」用 JOIN 导致分页条数错乱 | 用子查询计数，不用 JOIN + GROUP BY |
| 大厅与看板统计口径不一致 | 大厅只查 WAITING，看板明确写死是否含已取消、已下架（见第 5 节阶段 10） |
| 信用分越界 | 加分用 `LEAST(credit+3,100)`，减分用 `GREATEST(credit-3,0)`，库层不用 CHECK 约束兜底也可 |
| 前端写死数字 | 验收时全局搜索 html/js 中的魔法数字，除示例占位必须来自接口 |
| 已下架任务还能通过 URL 打开 | 详情接口对 offline=1 返回 1004 或明确「已下架」状态，页面不显示操作按钮 |
| 浏览器缓存旧 JS | 页面脚本引用加版本参数 `?v=1`，联调阶段手动强刷 |

---

## 7. PRD 验收标准与阶段映射

| PRD 第 10 节条目 | 覆盖阶段 |
| --- | --- |
| 1–6 账号与登录 | 阶段 4 |
| 7–13 大厅（列数、卡片信息、筛选、搜索、排序、只看待接单、分页） | 阶段 5 |
| 14–17 发布与信用拦截 | 阶段 5、阶段 8 |
| 18–24 状态流转、聊天发图、送达、确认、自动确认 | 阶段 6、阶段 7、阶段 9 |
| 25–28 评价、信用加减、充值、排行榜 | 阶段 7、阶段 8 |
| 29–34 取消、放弃、申诉提交与管理端处理 | 阶段 6、阶段 8、阶段 11 |
| 35–39 看板、管理端下架、重启保留、离线可用、组件页 | 阶段 10、阶段 11、阶段 12 |

阶段 12 结束时，这张表里每个阶段都要有对应的「已通过」记录，缺一条不算交付。
