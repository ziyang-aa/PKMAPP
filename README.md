# 小松果（PKMAPP）

> **当前版本：`v4.0.0`**（品牌正式更名为「小松果」｜Java 11 + Kotlin 2.2 混编、方案 A 双空间 MVVM + `StateFlow`、Room `v2` 多账号隔离、Spring Boot 3 + MySQL 8.4 云同步后端）

静谧奢华暖纸手账（Quiet-Luxury Warm Paper & Editorial Hairline）× 手绘蜡笔森林绘本风格的个人 **生活记账 + 学习专注** 双空间 Android 应用及配套云同步服务端。完整保留 14 个自绘 `Canvas` 组件，离线优先，支持对接自建 Spring Boot + MySQL 8.4 实现多账号云端增量同步。

---

## v4.0.0 更新说明（相对 v3.0.0）

1. **品牌升级与方案 A 双空间重构**：
   - 应用更名为 **小松果**；首页（`HomeFragment`）顶部改为左置头像胶囊（拉出暖瓷白个人侧边栏 `HomeDrawerController`）+ 右置云同步状态徽章。
   - 生活小屋底部导航收口为对称 4 格（`明细 / 图表 / 攒钱 / 小金库`）+ 右下松绿悬浮 `＋ 记一笔`；原个人页拆分为轻量 **小金库（`VaultFragment`）**、左侧个人抽屉与独立 **意见反馈中心（`FeedbackFragment`）**。
2. **三段式全中文登录门禁与可动小松鼠陪伴**：
   - 新增 `AuthActivity`（欢迎回来选号 / 登录注册分流 / Material 3 浮动标签独立输入表单 + `先以游客身份体验`），并优化全部按压态与游客合并引导。
   - 新增手绘可动吉祥物 `SquirrelMascotView`（呼吸、招手、点击互动、校验摇头与欢迎弹跳）、`SquirrelSpeechBubble` 提示气泡及全站 60fps `ForestLoadingView` 加载动效。
3. **Room `v1 → v2` 多账号隔离与安全凭证体系**：
   - 全部 12 张业务表升级为 `ownerId` 复合主键 + 同步元字段（`updatedAt` / `deletedAt` / `syncStatus` / `clientRequestId`），新增 `local_accounts` 与 `feedback_records` 表及无损迁移器 `MIGRATION_1_2`（导出 `app/schemas/.../2.json`）。
   - 本地采用 PBKDF2 密码哈希 + Android Keystore AES-256-GCM 会话存储（`CryptoSessionStore`），支持离线账号自动升云与全角 URL/重复 `http://` 规范化。
4. **配套 Spring Boot 3.5.11 + MySQL 8.4 纯接口云同步后端（`server/`）**：
   - 提供完整建表脚本（17 张表，`server/src/main/resources/sql/schema.sql`）、HS256 JWT + BCrypt 鉴权与 `sys_user_tokens` 刷新轮换、12 张业务表 LWW/幂等增量推拉同步（`/api/v1/sync/push` & `/pull`）、意见反馈与管理员回复接口，已通过 `PkmServerIntegrationTest` 实库验证与真机 USB 联调。
5. **学习小屋专注卡、选科交互、锁屏计时通知与全局弹窗去套框**：
   - `StudyFocusTimerCard` 升级为报刊发丝线时长选择条、松绿釉面胶囊按钮与原地防闪刷更新（`updateSessionGoalInPlace`）。
   - 科目库改为报刊发丝线双标签，点击「加入书架」通过 `StudySheet.popTo(formPane)` 直达计划书架；底部弹窗全面接入 Android 15/16 `WindowInsetsCompat.Type.ime()` 键盘避让。
   - `StudyNotifications` + `FocusTimerService` 新增开始前通知权限引导，并采用静默 `IMPORTANCE_DEFAULT` + `VISIBILITY_PUBLIC` 渠道（`study_timer_live`）在锁屏与通知栏实时展示学习时长。
   - 参考 Material Design 3 与 shadcn/ui 重做全局 `ForestDialogBuilder`，彻底去除多层方框套方框，长按钮文案自动堆叠杜绝截断。

### 历史版本摘要（v3.0.0）
- 接入 AGP `9.1.1` + Kotlin `2.2.10` + `KSP` + `Room` + `Preferences DataStore` + `WorkManager` 提醒兜底与 `FocusTimerService` 前台保活；拆解巨类为双空间 MVVM + `RecyclerView DiffUtil` 列表，并启用 Release R8 混淆与资源压缩。

---

## 核心功能

### 1. 森林门厅（Home）
- 顶栏左置头像胶囊（拉出个人侧边栏：账号切换、资产、多账本、分类管理、云同步与备份、意见反馈）+ 右置同步状态徽章。
- 手绘蜡笔双小屋全景入口：**学习小屋（松栗）** 与 **生活小屋（小刺猬与小芽）**。
- **今日速览**：汇总今日专注时长、待办事项数与本月支出，点击直达对应页面；存在进行中专注会话时顶部显示快捷续接条。

### 2. 学习小屋（Study Space）
- **专注（森林自习室）**：支持多学习计划（考研模板 / 期末课程 / 自定义科目书架）、正计时 / 倒计时切换、报刊发丝线时长选择、屏幕常亮、锁屏实时计时通知与通知栏暂停/继续/结束。
- **待办（独立草稿本）**：互斥三分区 **「今天（逾期置顶 + 今日）/ 计划（未来按日 + 随时可做）/ 已完成（按完成时间倒序日志）」**，支持自绘瓷白日历按日筛选与独立截止闹钟。
- **学习足迹（统计）**：累计专注摘要、环形/条形/每日趋势图表、6 周专注热力图、科目汇总与历史手记搜索/补记/待回顾。
- **我的小林地（成长花园）**：专注累计水滴与每日达标种子，浇灌养成手绘蜡笔成树并收录至林地收藏。

### 3. 生活小屋（Life Space）
- **明细**：多账本切换、本月收入/支出/结余卡片、按日期分组的交易流水，内嵌 **汇率换算**（24 种 ECB 货币批量同步与缓存）与 **借钱统计**。
- **图表**：周 / 月 / 年收支趋势折线图与分类占比排行。
- **记一笔**：23+1 项支出与 10+1 项收入分类（支持自定义）、四则计算器与借贷自动联动。
- **攒钱**：创建多个攒钱心愿目标，记录存入进度与幼芽成长阶段。
- **小金库（Vault）**：汇总净资产、负债、多账本、自定义分类、汇率/借钱工具及备份入口。

---

## 项目结构

```text
PKMAPP/
├─ README.md                                  # 项目说明与结构导航
├─ AGENTS.md                                  # 项目 Agent 工程与设计总规范
├─ DESIGN.md                                  # 视觉与交互设计规范（静奢暖纸体系）
├─ docs/
│  ├─ HANDOFF.md                              # 轻量会话交接备忘
│  ├─ V4-PLAN-SCHEME-A-AND-MYSQL.md           # v4.0 方案 A 与多账号/云同步设计说明
│  ├─ SERVER-MYSQL8-AND-SPRINGBOOT-CONTRACT.md # 服务端数据库表结构与 REST 接口契约
│  ├─ REFACTORING-PLAN.md                     # 渐进式重构总计划
│  └─ REFACTORING-CHANGELOG.md                # 重构变动记录
├─ server/                                    # Spring Boot 3.5.11 + MySQL 8.4 纯接口云同步服务端
│  ├─ pom.xml                                 # Maven 构建配置
│  ├─ README.md                               # 服务端启动与联调说明
│  └─ src/
│     ├─ main/java/com/example/pkmapp/server/ # auth / sync / feedback / admin / security / common
│     ├─ main/resources/sql/schema.sql        # MySQL 8.4 完整 17 表 DDL
│     └─ test/java/.../PkmServerIntegrationTest.java # 实库全链路集成测试
└─ app/                                       # Android 客户端模块
   ├─ build.gradle.kts                        # 构建配置（versionName = 4.0.0）
   ├─ schemas/                                # Room 导出的数据库版本 Schema JSON（1.json / 2.json）
   └─ src/
      ├─ debug/AndroidManifest.xml            # Debug 包本机 HTTP 联调配置
      ├─ main/java/com/example/pkmapp/
      │  ├─ SplashActivity.java / MainActivity.java
      │  ├─ account/                          # 三段式登录门禁（AuthActivity）、侧边栏与云同步弹窗
      │  ├─ core/                             # database(Room v2) / datastore / account / security / sync / feedback / work
      │  ├─ forest/                           # 暖纸设计系统、自绘组件、SquirrelMascotView、ForestDialogBuilder 等
      │  ├─ home/                             # 森林门厅（HomeFragment）
      │  ├─ study/ & todo/                    # 学习小屋四页 MVVM、专注卡片、自绘图表/热力图/花园与前台服务
      │  ├─ life/ & data/                     # 生活小屋共享 MVVM 状态层与 Room 仓库
      │  ├─ details/ charts/ record/ savings/ borrowing/ exchange/ vault/ feedback/ backup/ profile/
      ├─ test/                                # 44 个 JVM 本地单元测试文件
      └─ androidTest/                         # 9 个真机/模拟器 UI 与数据库迁移测试
```

---

## 数据存储、云同步与备份

- **离线优先 + 多账号隔离**：全部业务数据本地持久化于 **Jetpack Room（SQLite `pkm_app.db` v2）**，按 `ownerId` 严格隔离；个人偏好与加密凭证由 **DataStore + Android Keystore** 管理。
- **自建云同步（可选）**：在首页左上角头像 → **数据备份与云同步** 中配置服务端地址（模拟器 `http://10.0.2.2:8080`，USB 真机配 `adb reverse tcp:8080 tcp:8080` 后填 `http://127.0.0.1:8080`）并登录账号，即可与本地/云端 Spring Boot + MySQL 8.4 双向增量同步。
- **本地 JSON 备份**：支持向后兼容的 `.pkmbackup.json` 导出与去重合并导入，学习小屋同样支持独立 JSON 备份。

---

## 构建、测试与联调

### 1. Android 客户端（Windows PowerShell）

```powershell
# 编译 + 全部本地单元测试 + Debug APK 打包
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestSources assembleDebug --console=plain

# 连接模拟器或真机运行 UI 与迁移测试
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

调试安装包输出路径：`app/build/outputs/apk/debug/app-debug.apk`。

### 2. Spring Boot 后端服务（需本地 MySQL 8.4 已创建 `pkmapp` 库）

```powershell
# 运行实库全链路集成测试
mvn -f d:\pkmAPP\server\pom.xml test

# 打包并启动后端服务（默认监听 8080）
mvn -f d:\pkmAPP\server\pom.xml package -DskipTests
java -jar d:\pkmAPP\server\target\songguo-server-4.0.0.jar
```

### 3. Release 混淆打包与签名

Release 构建已启用 R8 代码混淆与资源压缩（`isMinifyEnabled = true`、`isShrinkResources = true`）。在根目录 `local.properties`（或环境变量 `PKM_STORE_FILE`、`PKM_STORE_PASSWORD`、`PKM_KEY_ALIAS`、`PKM_KEY_PASSWORD`）配置签名信息后执行：

```powershell
.\gradlew.bat assembleRelease
```
