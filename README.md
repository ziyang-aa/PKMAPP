# 小松果（PKMAPP）

> **当前版本：`v5.0.0`**（静谧暖纸手账 × 双空间 MVVM + `StateFlow`、Room `v3` 多账号隔离与月度预算同步、Spring Boot 3 + MySQL 8.4 静默云同步、森林白噪音与暖纸桌面小组件）

静谧奢华暖纸手账（Quiet-Luxury Warm Paper & Editorial Hairline）× 手绘蜡笔森林绘本风格的个人 **生活记账 + 学习专注** 双空间 Android 应用及配套云同步服务端。完整保留 14 个自绘 `Canvas` 组件，离线优先，支持对接自建 Spring Boot + MySQL 8.4 实现多账号自动增量云同步。

---

## v5.0.0 更新说明（相对 v4.0.0）

1. **月度预算 Room `v2 → v3`、MySQL 自动迁移与 5 秒静默防抖云同步**：
   - `LedgerEntity` 与服务端 `biz_ledgers` 无损新增 `monthlyBudgetCents` / `monthly_budget_cents`（Android 侧 `MIGRATION_2_3` + `app/schemas/.../3.json`，服务端新增 `SchemaMigrationRunner` 启动自动无损补列），同步保持 `.pkmbackup.json` 向后兼容。
   - `SyncRepository` 补齐 `ledgers` 双向推拉与 LWW 合并；`SyncWorker.scheduleDebouncedSync()` 在已联网云登录态下实现 5 秒防抖后台静默同步（游客与离线态静默跳过，绝不弹窗打扰）。
2. **生活小屋发丝预算条、暖瓷白去套框弹窗与攒钱卡片精简**：
   - 新增共享抽屉底座 `ForestSheetHelper`（暖瓷白 `#FFFEFA` 画布 + Android 15/16 `WindowInsetsCompat.Type.ime()` 键盘避让 + 报刊发丝底线输入）。
   - 明细页余额卡下方新增零套框报刊发丝线月度预算条（支持本月及历史月份对比复盘，支出 ≥80% 暖琥珀预警，>100% 陶红超支提示）；明细旧版账本/新建/月份/预算与删除弹窗全部升级为暖瓷白抽屉或无套框 `ForestDialogBuilder`；借钱弹窗补齐键盘避让。
   - 攒钱心愿卡去除卡片内部常驻多层输入套框，精简为「插画 + 进度 + 底部一行『＋ 存入』与『编辑 ›』」，点击分别弹出快捷存入抽屉（`+50/+100/+200`）与新建/编辑暖瓷白抽屉。
3. **图表页顶栏与水彩伙伴极简重塑（3+B 方案）**：
   - `ChartsFragment` 将 `支出 / 收入` 升级为 `22sp / 19sp` 衬线发丝线切换并同行右置紧凑 `周 / 月 / 年`；时间翻页条（`‹ 2026年11月 ▾ ›`）无框收纳入瓷白趋势卡顶栏并支持周/月/年三模式一键翻页与暖瓷白年月抽屉；水彩伙伴（小熊/小松鼠）轻栖于趋势卡右上沿，点击可直接切换支出/收入。
4. **考期倒计时与锁屏通知实时进度条**：
   - 新增 `ExamCountdownFormatter`，复用 `StudyPlan.due` 在专注页与首页「今日速览」右侧展示考期倒计时。
   - `StudyNotifications` 锁屏/通知栏计时通知补齐实时进度条与完成百分比文案，`FocusTimerService` 刷新频率提升至 15 秒。
5. **专注森林白噪音引擎**：
   - 新增原生 `AudioTrack` 实时合成引擎 `ForestSoundEngine`（**林间微雨 / 木屋炉火 / 林梢清风**，零音频体积膨胀、无缝无限循环、支持 5 秒试听与音量调节），与 `FocusTimerService` 生命周期联动，实现开始即播、暂停/结束自动淡出静音与音频焦点礼让。
6. **Android 暖纸风双空间桌面小组件（免开屏直达）**：
   - 新增 `FocusWidgetProvider`（专注自习小组件）与 `LifeBudgetWidgetProvider`（生活支出与月度预算小组件），默认 `4×2` 支持缩放；通过 `SplashActivity → MainActivity` 的 `EXTRA_WIDGET_DESTINATION` 实现免开屏直达专注页 / 记一笔 / 明细页，并在数据写入后实时刷新组件。

### 历史版本摘要
- **`v4.0.0`（`release/v4.0`）**：正式更名为 **小松果**；落地「方案 A」双空间（首页左置头像胶囊侧边栏 + 右置同步徽章、生活小屋 4 格底栏 + 悬浮 `＋ 记一笔`、`VaultFragment` 小金库与 `FeedbackFragment` 意见反馈）；新增三段式全中文登录门禁（`AuthActivity`）、可动小松鼠 `SquirrelMascotView` 与 `ForestLoadingView`；升级 Room `v1 → v2` 多账号隔离；新建 Spring Boot 3.5.11 + MySQL 8.4 云同步后端（`server/`）；重做专注卡片、科目库双标签与无套框全局 `ForestDialogBuilder`。
- **`v3.0.0`（`codex/v3-release`）**：接入 AGP `9.1.1` + Kotlin `2.2.10` + Room + Preferences DataStore + WorkManager + `FocusTimerService`；拆解巨类为双空间 MVVM + `RecyclerView DiffUtil` 列表，并启用 Release R8 混淆与资源压缩。

---

## 核心功能

### 1. 森林门厅（Home）
- 顶栏左置头像胶囊（暖瓷白个人侧边栏：账号切换、资产、多账本、分类、云同步与备份、意见反馈）+ 右置同步状态徽章。
- 手绘蜡笔双小屋全景入口：**学习小屋（松栗）** 与 **生活小屋（小刺猬与小芽）**。
- **今日速览**：汇总今日专注时长、待办事项数、本月支出与考期倒计时，点击直达对应页面；存在进行中专注会话时顶部显示快捷续接条。

### 2. 学习小屋（Study Space）
- **专注（森林自习室）**：多学习计划（考研模板 / 期末课程 / 自定义科目书架）、考期倒计时、正/倒计时切换、发丝线时长选择、森林白噪音（雨声/炉火/清风）、屏幕常亮与锁屏实时进度通知（含暂停/继续/结束）。
- **待办（独立草稿本）**：互斥三分区 **「今天（逾期置顶 + 今日）/ 计划（未来按日 + 随时可做）/ 已完成（按完成时间倒序日志）」**，自绘瓷白日历按日筛选与独立截止闹钟。
- **学习足迹（统计）**：累计专注摘要、环形/条形/每日趋势图表、6 周专注热力图、科目汇总与历史手记搜索/补记/待回顾。
- **我的小林地（成长花园）**：专注累计水滴与每日达标种子，浇灌养成手绘蜡笔成树并永久收录。

### 3. 生活小屋（Life Space）
- **明细**：多账本切换、本月收入/支出/结余卡片、**报刊发丝线月度预算进度条**（支持历史月复盘与预警/超支提示）、按日期分组流水，内嵌 **汇率换算**（24 种 ECB 货币批量同步）与 **借钱统计**。
- **图表**：3+B 极简报刊风顶栏、周/月/年三模式一键翻页与年月抽屉、趋势折线图与分类排行。
- **记一笔**：23+1 项支出与 10+1 项收入分类（支持自定义）、四则计算器与借贷自动联动。
- **攒钱**：清爽精简的攒钱心愿卡、快捷存入抽屉与幼芽成长阶段。
- **小金库（Vault）**：净资产、负债、多账本、分类管理、工具入口与云/本地备份。

### 4. 桌面小组件（App Widgets）
- **专注自习小组件**：显示当前计划、考期倒计时与今日专注时长，一键免开屏直达专注页。
- **生活预算小组件**：显示当前账本本月支出、预算进度条与余量，一键免开屏直达「记一笔」或「明细」。

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
│     ├─ main/java/com/example/pkmapp/server/ # auth / sync / feedback / admin / security / common(含 SchemaMigrationRunner)
│     ├─ main/resources/sql/schema.sql        # MySQL 8.4 完整 17 表 DDL（含 biz_ledgers.monthly_budget_cents）
│     └─ test/java/.../PkmServerIntegrationTest.java # 实库全链路集成测试
└─ app/                                       # Android 客户端模块
   ├─ build.gradle.kts                        # 构建配置（versionCode = 5, versionName = 5.0.0）
   ├─ schemas/                                # Room 导出的数据库版本 Schema JSON（1.json / 2.json / 3.json）
   └─ src/
      ├─ debug/AndroidManifest.xml            # Debug 包本机 HTTP 联调配置
      ├─ main/java/com/example/pkmapp/
      │  ├─ SplashActivity.java / MainActivity.java # 含小组件免开屏直达路由
      │  ├─ account/                          # 三段式登录门禁（AuthActivity）、侧边栏与云同步弹窗
      │  ├─ core/                             # database(Room v3) / datastore / account / security / sync / feedback / work
      │  ├─ forest/                           # 暖纸设计系统、自绘组件、ForestSheetHelper、SquirrelMascotView、ForestDialogBuilder
      │  ├─ home/                             # 森林门厅（HomeFragment）
      │  ├─ study/ & todo/                    # 学习小屋 MVVM、ExamCountdownFormatter、ForestSoundEngine、FocusTimerService 等
      │  ├─ life/ & data/                     # 生活小屋共享 MVVM、MonthlyBudgetEvaluator 与 Room 仓库
      │  ├─ widget/                           # FocusWidgetProvider、LifeBudgetWidgetProvider、ForestWidgets
      │  ├─ details/ charts/ record/ savings/ borrowing/ exchange/ vault/ feedback/ backup/ profile/
      ├─ test/                                # 47 个 JVM 本地单元测试文件（134 项用例）
      └─ androidTest/                         # 9 个真机/模拟器 UI 与数据库迁移测试
```

---

## 数据存储、云同步与备份

- **离线优先 + 多账号隔离**：业务数据本地持久化于 **Jetpack Room（SQLite `pkm_app.db` v3）**，支持 `v1 → v2 → v3` 无损自动迁移与按 `ownerId` 严格隔离；偏好与加密会话由 **DataStore + Android Keystore** 管理。
- **自建云同步 + 5 秒静默自动同步（可选）**：在首页左上角头像 → **数据备份与云同步** 中配置服务端地址（模拟器 `http://10.0.2.2:8080`，USB 真机配 `adb reverse tcp:8080 tcp:8080` 后填 `http://127.0.0.1:8080`）并登录账号后，支持手动立即同步及数据变更后 5 秒防抖后台静默同步。
- **本地 JSON 备份**：支持向后兼容的 `.pkmbackup.json` 导出与去重合并导入（兼容月度预算字段），学习小屋支持独立 JSON 备份。

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
# 运行实库全链路集成测试（启动时自动无损补齐新增字段）
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
