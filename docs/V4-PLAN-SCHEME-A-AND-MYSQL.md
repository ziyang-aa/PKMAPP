# 芽叶记 Android v4.0 演进计划：方案 A 双空间架构 + 登录注册 + MySQL 云同步

> **文档定位**：本计划承接 Android `v3.0.0` 与微信小程序 `pkmapp_wx v3.0`（均已完成），面向后续购买云主机并自建 MySQL 后端的长期演进规划。整体采用 **Offline-First（Room 离线优先 + 异步同步 MySQL）**，既保证单机体验零延迟，也为 Android、微信小程序和未来 Web 管理后台的三端数据统一打下基础。

---

## 1. 当前基线与已有可复用资产

### 1.1 Android 端现况（`v3.0.0`）
- **构建配置**（`app/build.gradle.kts`）：Kotlin/Java 11 混编，`compileSdk 36`，`minSdk 24`，已接入 `Room 2.8.4`（KSP + Schema 导出至 `app/schemas/`）、`Preferences DataStore 1.1.7`、`WorkManager 2.10.5`、`Coroutines`、`ViewModel`。
- **本地数据库**（`core/database/AppDatabase.kt`）：`version = 1`，已落地 **12 张结构化表**（`ledgers`、`transactions`、`borrowing_records`、`savings_goals`、`custom_categories`、`study_plans`、`study_subjects`、`study_records`、`study_tasks`、`study_reviews`、`study_garden_state`、`todo_items`）。
- **导航与视图底座**（`MainActivity.java`、`HomeFragment.java`）：
  - `HomeFragment` 已经是无底栏的双门森林大厅，目前顶栏左侧为叶芽 Logo + 标题，尚无头像触发器与侧边栏。
  - 学习小屋底部采用 `FrostedNavigationBar`（专注 / 待办 / 学习足迹 / 小林地）；生活小屋底部目前为 5 项（明细 / 图表 / 记一笔 / 攒钱 / 我的），其中 `ProfileFragment.kt` 混放了全局账号设置（头像、昵称、打卡、JSON 备份）和生活专属设置（期初资产负债、分类、多账本）。
  - 既有视觉规范见根目录 `AGENTS.md` 与 `DESIGN.md`（暖纸手账 `#F5F1E7` / 瓷白 `#FFFEFA` / 松绿 `#456444`、三图层 Edge-to-Edge、自绘 Canvas 组件与 `ForestCalendarView` 复用）。

### 1.2 微信小程序端协同基础（`pkmapp_wx v3.0`）
- 已先行落地 **方案 A**（森林双门大厅 + 左侧个人抽屉 + 双小屋悬浮底栏），并在 `packageLife/pages/vault` 与 `packageLife/pages/feedback` 跑通「小金库（资产与账本）」和「用户反馈中心」。
- `pkmapp_wx/config.js` 已预留 `customApiBaseUrl` 网关切换位，未来后端部署后可直接让小程序与 Android 共用同一个 MySQL 数据库。

---

## 2. v4.0 总体架构方案（Offline-First + MySQL 8）

```text
+-----------------------+        +--------------------------+
|   Android App (v4)    |        |   微信小程序 (pkmapp_wx)  |
|  - Room 2.8 (离线优先) |        |  - customApiBaseUrl 网关 |
|  - WorkManager 增量同步|        +------------+-------------+
+-----------+-----------+                     |
            | HTTPS + JWT                     | HTTPS + Code/Token
            +----------------+----------------+
                             |
                             v
            +---------------------------------+
            | 自建轻量 API 服务 (Spring/Node) |
            | Auth / Sync / Feedback / Logs   |
            +----------------+----------------+
                             |
                             v
            +---------------------------------+
            |         MySQL 8 数据库          |
            |  用户增长 / 账本流水 / 反馈 / 日志 |
            +---------------------------------+
                             ^
                             |
            +----------------+----------------+
            |   Web 管理后台 (后续配套搭建)    |
            +---------------------------------+
```

### 设计要点
1. **离线优先、有网自动同步**：Android 记账、专注和待办操作全部先写本地 Room（毫秒级响应，地铁/断网照常使用），记录变更打上 `DIRTY` 标记与 `updatedAt` 时间戳；有网时由 `SyncRepository` 与 `WorkManager` 增量推拉。
2. **无缝衔接未来服务器**：在还没购买服务器前，先完成 Room 升级、本地账号与接口契约，支持本地模拟器 / 本地后端联调；购买云主机后只需配置 `BASE_URL` 即可连上真实 MySQL。
3. **三端账号互通预留**：在后端用户表设计手机号/邮箱密码账号与微信 `openid` 绑定能力，支持「先游客离线体验 → 注册登录账号 → 把本机已有 Room 数据一键绑定到新账号」，避免丢数。

---

## 3. 分阶段实施路线（推荐顺序）

### 阶段 1：交互原型设计与方案确认（UI 先行）
- **目标**：在修改 Android 原生布局前，先做一份适配手机端尺寸的可交互 HTML 原型稿（存放至会话 scratch/artifact 目录），方便直观确认交互细节：
  1. **大厅左侧抽屉 Drawer**：左上角头像胶囊位置、暖瓷白抽屉内部排布（未登录/已登录态卡片、登录注册入口、打卡日历、**用户反馈中心**、JSON 导入导出、隐私与退出登录）。
  2. **生活小屋底栏与「小金库」整合方案**：将 `ProfileFragment` 中的多账本、期初资产/负债、自定义分类收拢至生活小屋内部（例如第 5 格替换为「小金库 / 理财工具」，或整合进明细/顶部入口），保留中央「记一笔」视觉重心。
  3. **登录 / 注册与反馈页样式**：延续暖纸发丝线手账质感（无框底线输入、单主按钮、清晰错误提示与游客合并提示）。

### 阶段 2：Android 端方案 A 导航与界面重构
- **大厅与左侧边栏**：
  - 在 `MainActivity` 根布局引入平滑左侧抽屉（可基于 `DrawerLayout` 或自定义平滑动效容器，保持三图层 Edge-to-Edge 全屏穿透，抽屉内容避让状态栏与手势条 `WindowInsets`）。
  - 改造 `HomeFragment.java` 顶栏左侧为圆框头像胶囊（复用 `AvatarStore` 与当前昵称），点击滑出全局个人抽屉；保留双门入口、今日专注/待办/支出速览。
- **生活小屋结构重组**：
  - 拆分生活专属管理页 `VaultFragment`（承载净资产、期初资产与负债、多账本切换/新建/删除、自定义分类、攒钱、借钱、汇率统一入口）与全局个人功能页/抽屉，保持 `MainActivity` 底部 `FrostedNavigationBar` 与学习小屋对称美观。
  - 新增 `FeedbackFragment`（或抽屉内入口跳转页）：分类标签、问题描述、联系方式、历史反馈列表、提交防抖与离线暂存。
- **兼容测试锚点**：同步调整 `AppDestination` 及相关单元测试、UI 锚点检查，确保回退、通知直达与双小屋切换顺滑稳定。

### 阶段 3：Room 数据库 `v1 -> v2` 迁移与多账号同步元数据
- **Schema 升级（保留完整 `1.json -> 2.json` 迁移路径）**：
  - 新增本地 `users_session`（或 Preferences/Encrypted 存储当前登录态与 JWT Token）及待上传队列 / 反馈缓存表 `feedback_drafts`。
  - 业务主表（`ledgers`、`transactions`、`borrowing_records`、`savings_goals`、`custom_categories`、`study_plans`、`study_subjects`、`study_records`、`study_tasks`、`todo_items`）补充同步元字段：
    - `ownerId: String`（默认值 `"local_guest"`，绑定账号后批量更新或隔离展示）
    - `updatedAt: Long`（客户端最新修改毫秒时间戳）
    - `deletedAt: Long`（`0` 表示有效，`> 0` 表示软删除待同步墓碑）
    - `syncStatus: Int`（`0 = SYNCED`, `1 = PENDING_PUSH`）
    - `clientRequestId: String`（写入幂等键，防止弱网重试在 MySQL 端产生重复账单）
- **无损迁移**：编写 `MIGRATION_1_2` 并补充 Room `Migration` 单元测试，确保升级覆盖安装时原有单机数据 100% 保留。

### 阶段 4：自建后端接口契约与 MySQL 8 建表设计
- **技术栈建议**（择一熟悉的即可，Node/Express + Prisma/MySQL2 或 Spring Boot + MyBatis-Plus 皆可）：
  - 鉴权：JWT（AccessToken + RefreshToken）+ 密码 `bcrypt` 哈希 + 注册/登录接口限流。
  - 核心接口：
    - `POST /api/v1/auth/register` / `login` / `bind-guest-summary`
    - `POST /api/v1/sync/push`（按 `clientRequestId` 幂等落库，返回服务端确认时间戳）
    - `GET /api/v1/sync/pull?since=<cursor>`（增量拉取最新变更）
    - `POST /api/v1/feedbacks` & `GET /api/v1/feedbacks/mine`
    - `POST /api/v1/logs/client-error`（上报崩溃栈与关键同步报错，方便后台排查）
  - **MySQL 8 核心表**：
    - `sys_users` (`id`, `phone_or_email`, `password_hash`, `wx_openid`, `nickname`, `avatar_url`, `created_at`)
    - 业务表与 Android/小程序 8+ 张表一一映射，核心索引统一带 `(user_id, updated_at)` 与 `(user_id, client_request_id)` 唯一键。

### 阶段 5：Android 接入网络同步客户端 + 三端联调上线
- **网络与安全**：引入 `OkHttp` + `Retrofit`（或 Ktor Client）与 `androidx.security:security-crypto` 保存登录凭证，配置 Release 混淆规则。
- **后台同步调度**：利用已有 `WorkManager` 增加 `SyncWorker`（Wi-Fi/联网自动补传脏数据 + 下拉同步；登录成功后提示是否将本地游客数据合并绑定到云账号）。
- **扩展收口**：部署到自购云主机后，同步把 `pkmapp_wx/config.js` 的 `customApiBaseUrl` 指向同一域名，最后按需补一个 Web 管理端看用户增长、反馈和日志。

---

## 4. 验收参考
- 每个阶段完成后运行 `./gradlew.bat testDebugUnitTest compileDebugAndroidTestSources assembleDebug --console=plain` 验证编译与单元测试通过。
- 涉及 Room 迁移与 UI 改造时，在模拟器上核对升级无丢数、侧边栏滑出无状态栏遮挡、双小屋底栏切换及登录/游客切换数据隔离。
