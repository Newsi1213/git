CampusQuest 校园互助悬赏平台 · 运行与验收说明
============================================================

一、运行环境
------------------------------------------------------------
1. JDK 11（本机示例路径 E:\0_workspace\java11，可按实际安装位置调整）
2. MySQL 8.0，数据库名 campus_quest（utf8mb4）
3. Apache Tomcat 9（项目内置免安装版：E:\MyApp\tools\tomcat9，禁止使用 Tomcat 10/11）
4. 浏览器访问 http://localhost:8080/campusquest/login.html

二、启动与停止
------------------------------------------------------------
首次部署：
1. 确认 MySQL 已启动。
2. 依次执行 server/sql/01_schema.sql、02_init.sql；需要演示数据时再执行 03_seed.sql。
3. 在 PowerShell 中执行 E:\MyApp\server\build.ps1 编译 Java 源码到 WEB-INF\classes。
4. 执行 E:\MyApp\server\start.ps1 启动 Tomcat。
5. 停止服务执行 E:\MyApp\server\stop.ps1。

说明：若 start.ps1 启动的窗口被关闭，可在 PowerShell 中用以下方式前台运行：
  $env:CATALINA_HOME='E:\MyApp\tools\tomcat9'
  $env:JAVA_HOME='你的 JDK 目录'
  $env:CATALINA_OPTS='-Dfile.encoding=UTF-8'
  & "$env:CATALINA_HOME\bin\catalina.bat" run

三、演示账号
------------------------------------------------------------
学生端：
  演示同学A  手机号 13800000001  密码 123456  信用 100，已发布 3 条待接单任务
  演示同学B  手机号 13800000002  密码 123456  信用 89，可接单但不能发布
  演示同学C  手机号 13800000003  密码 123456  信用 84，不能发布也不能接单
管理端：
  用户名 admin  密码 admin123
  入口：登录页「管理员入口」，登录后进入 admin/appeals.html

四、数据存储与持久化
------------------------------------------------------------
1. 业务数据全部保存在本机 MySQL 的 campus_quest 库。
2. 聊天图片与申诉截图保存在 E:\MyApp\uploads\yyyy\MM\dd\ 下，数据库只存相对路径。
3. 重启 Tomcat 或重启电脑后，数据库与 uploads 目录均保留；登录态保存在服务内存，重启后需重新登录。

五、备份与还原
------------------------------------------------------------
备份数据库（不要把密码写进脚本或文档，按需交互输入）：
  mysqldump -u root --default-character-set=utf8mb4 --routines --triggers campus_quest > backup.sql

备份图片：
  将 E:\MyApp\uploads 整个目录复制到备份位置。

还原：
  1. 新建空库 campus_quest（utf8mb4）。
  2. mysql -u root --default-character-set=utf8mb4 campus_quest < backup.sql
  3. 把备份的 uploads 目录复制回 E:\MyApp\uploads。
  4. 重启 Tomcat。

六、断网运行
------------------------------------------------------------
Vue2、ElementUI、axios 等前端依赖均已放在 server/src/main/webapp/assets/lib 下，页面不引用任何外网地址；
下载完成后运行期无需联网。

七、自动化测试
------------------------------------------------------------
测试脚本位于 E:\MyApp\server\test\，需先启动服务再运行，例如：
  powershell -NoProfile -ExecutionPolicy Bypass -File E:\MyApp\server\test\test_stage11.ps1
每个脚本输出 PASS/FAIL，末行给出本阶段是否全部通过。
脚本会通过接口与数据库交叉核对结果，测试数据保留在库中，不做自动清理。

八、目录结构
------------------------------------------------------------
E:\MyApp\PRD.md                  需求文档
E:\MyApp\DEV-PLAN.md             施工方案
E:\MyApp\wireframe\              线框图
E:\MyApp\tools\tomcat9\          Tomcat 9
E:\MyApp\server\sql\             建库、初始化、演示数据脚本
E:\MyApp\server\src\main\java\   后端源码（Servlet + JDBC + DAO + Filter）
E:\MyApp\server\src\main\webapp\ 前端页面与本地静态资源
E:\MyApp\server\test\            分阶段自动化测试脚本
E:\MyApp\uploads\                聊天图片与申诉截图
E:\MyApp\backup\                 数据库导出与图片备份

九、约定
------------------------------------------------------------
1. 后端只用 Servlet 3.1 + 原生 JDBC + DAO + 实体类 + Filter，不引入框架。
2. 前端只用 Vue2 + ElementUI + axios，不使用脚手架，不引用外部 CDN。
3. 页面统一自 1280px 起布局，不做移动端适配；界面文案全中文。
4. 未登录访问需要登录的接口返回 401 与中文提示，学生访问管理端接口返回 403。
