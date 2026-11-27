# 小松果（PKMAPP）

> **当前版本：`v5.0.0`** · 个人「生活记账 + 学习专注」双空间 Android 应用与云同步服务端

**小松果**是一款以暖纸手账与手绘森林绘本为视觉基调的个人生活与学习管理应用，面向大学生、备考人群及日常自律记录者，将**记账预算、攒钱借还、专注计时、学习计划、独立待办与小林地养成**融于统一克制的界面体验中。

客户端采用 **Java 11 + Kotlin 2.2 混编**、离线优先的 **Room + Preferences DataStore** 本地架构，完整保留 14 个自绘 `Canvas` 组件；服务端提供轻量级 **Spring Boot 3 + MySQL 8.4** REST 接口，支持多账号数据隔离与跨端增量同步。

---

## 核心功能

### 1. 森林门厅（双空间入口）
- **双小屋分流**：首页通过手绘全景门厅连接「学习小屋」与「生活小屋」，支持记住上次进入的小屋。
- **今日速览**：一屏呈现今日专注时长、待办数量、本月支出与考期倒计时，点击直达对应页面；存在进行中的专注会话时自动显示快捷续接入口。
- **个人中心与多账号**：顶部头像拉出侧边栏，支持多账号注册/登录与切换、游客体验及数据无缝合并、头像裁剪、每日签到、数据备份与意见反馈。

### 2. 生活小屋（财务与日常管理）
- **明细与月度预算**：支持多账本切换、按日分组流水与月度收支结余统计；提供按账本配置的**月度基准预算进度条**（支持本月与历史月份复盘，支出达 80% 暖色预警、超支红字提醒）。
- **统计图表**：提供收支切换与周 / 月 / 年三维度趋势折线图及分类排行，支持快速翻页、年月抽屉筛选，点击趋势卡右上角水彩伙伴即可快速切换支出与收入视图。
- **快捷记一笔**：内置常用支出/收入分类与自定义分类管理、四则计算器与借还记录自动联动。
- **攒钱心愿**：支持多目标攒钱进度追踪、成长阶段插画反馈与快捷存入/编辑抽屉。
- **小金库与实用工具**：集中管理净资产、负债、多账本与分类，内置 **24 种 ECB 货币实时汇率换算**（支持本地缓存离线换算）与 **借入/借出往来统计**。

### 3. 学习小屋（备考与专注陪伴）
- **森林自习室（专注计时）**：
  - 支持考研、期末与自定义多学习计划及科目书架，内置**考期倒计时**。
  - 支持正计时 / 倒计时切换、自定义专注时长、屏幕常亮及后台保活。
  - **锁屏实时通知**：专注期间在通知栏与锁屏界面展示已学时长、实时进度条，并提供免解锁「暂停 / 继续 / 结束」控制。
  - **森林白噪音**：内置原生实时合成环境音（林间微雨 / 木屋炉火 / 林梢清风），零额外音频体积占用、无缝循环，随专注计时自动启停并支持试听与音量调节。
- **独立待办草稿本**：按互斥三分区组织——**今天（逾期置顶 + 今日）/ 计划（未来按日 + 随时可做）/ 已完成（按实际完成时间倒序日志）**，支持自绘日历按日筛选与独立截止提醒。
- **学习足迹**：汇总累计专注次数、天数、日均时长与连续打卡，提供科目占比环形图、条形图、每日趋势、6 周专注热力图及学习手记搜索/补记。
- **我的小林地**：专注时长转化为水滴与每日达标种子，浇灌养成手绘成树并永久收录。

### 4. 桌面小组件（App Widgets）
- **专注自习小组件**：桌面实时显示当前学习计划、考期倒计时与今日专注时长，一键免开屏直达专注页。
- **生活预算小组件**：桌面展示当前账本本月支出、预算进度与余量，一键免开屏直达「记一笔」或「明细」。

---

## 技术架构

| 层级 | 技术选型与实现要点 |
| --- | --- |
| **Android 语言与构建** | Java 11 + Kotlin 2.2.10 混编、AGP 9.1.1、KSP、ViewBinding、Release R8 代码与资源压缩 |
| **界面与自绘组件** | 原生 View 体系 + 14 个自绘 `Canvas` 视图（日历、时钟进度条、统计图表、热力图、成长花园、草稿纸等）+ 悬浮毛玻璃底栏 + 统一暖瓷白抽屉与弹窗 |
| **架构与状态管理** | 双空间 MVVM（`ViewModel` + `StateFlow` + `Coroutines`）、`RecyclerView ListAdapter + DiffUtil` |
| **本地存储与安全** | Jetpack Room（SQLite `v3`，多账号 `ownerId` 复合主键隔离 + 自动化无损迁移 `v1→v2→v3`）、Preferences DataStore、PBKDF2 密码哈希、Android Keystore AES-256-GCM 会话加密 |
| **后台任务与音频** | `Foreground Service`（锁屏计时保活）、`WorkManager`（提醒重排 + 5 秒防抖后台静默云同步）、`AudioTrack` 实时环境音合成 |
| **云同步服务端（`server/`）** | Spring Boot 3.5.11 + MySQL 8.4 + Spring JDBC、HS256 JWT + BCrypt + 刷新令牌轮换、12 张业务表 LWW/幂等增量推拉同步、启动自动表结构补列 |

---

## 项目结构

```text
PKMAPP/
├─ README.md                                  # 项目文档
├─ DESIGN.md                                  # 视觉与交互设计规范
├─ build.gradle.kts / settings.gradle.kts     # Android 根工程 Gradle 配置
├─ server/                                    # Spring Boot 3 + MySQL 8.4 云同步服务端
│  ├─ pom.xml                                 # Maven 构建配置
│  ├─ README.md                               # 服务端启动与环境变量说明
│  └─ src/
│     ├─ main/java/com/example/pkmapp/server/
│     │  ├─ auth/                             # 注册、登录、JWT 刷新与账号注销
│     │  ├─ sync/                             # 12 张业务表增量推送与拉取同步
│     │  ├─ feedback/ & admin/                # 意见反馈提交与管理端回复
│     │  ├─ security/                         # JWT 鉴权拦截器与配置
│     │  └─ common/                           # 统一响应、异常处理、健康检查与自动补列迁移
│     ├─ main/resources/sql/schema.sql        # MySQL 8.4 完整建表脚本（17 张表）
│     └─ test/java/.../PkmServerIntegrationTest.java # 实库全链路集成测试
└─ app/                                       # Android 客户端模块
   ├─ build.gradle.kts                        # 模块配置（minSdk 24, targetSdk 36, version 5.0.0）
   ├─ schemas/                                # Room 导出的数据库版本 Schema JSON（v1 / v2 / v3）
   └─ src/
      ├─ debug/AndroidManifest.xml            # Debug 本地联调明文 HTTP 配置
      ├─ main/java/com/example/pkmapp/
      │  ├─ SplashActivity.java / MainActivity.java # 启动路由、小组件免开屏直达与双空间导航
      │  ├─ account/                          # 登录注册门禁、个人侧边栏与云同步弹窗
      │  ├─ core/                             # Room 数据库、DataStore、加密会话、云同步、反馈与 WorkManager
      │  ├─ forest/                           # 统一视觉组件库、自绘控件、吉祥物动效、暖瓷白抽屉与无套框弹窗
      │  ├─ home/                             # 首页双空间门厅与今日速览
      │  ├─ study/ & todo/                    # 学习小屋四页 MVVM、考期倒计时、森林白噪音、锁屏前台服务与独立待办
      │  ├─ life/ & data/                     # 生活小屋共享 MVVM、月度预算评估器与本地仓库
      │  ├─ widget/                           # 专注自习与生活预算桌面小组件
      │  └─ details/ charts/ record/ savings/ borrowing/ exchange/ vault/ feedback/ backup/
      ├─ test/                                # 47 个 JVM 本地单元测试文件（134 项用例）
      └─ androidTest/                         # 9 个 UI 交互与 Room 数据库迁移集成测试
```

---

## 数据存储、云同步与备份

- **离线优先**：应用可完全脱离网络独立运行，所有业务数据以 Room SQLite 持久化保存在本地，并按账号 `ownerId` 严格隔离。
- **可选自建云同步**：部署 `server/` 服务端后，在应用内「个人侧边栏 → 数据备份与云同步」填写服务地址并登录，即可手动同步或在数据变更后自动触发 **5 秒防抖后台静默同步**（游客或断网状态自动跳过）。
- **本地 JSON 备份**：生活记账支持导出与导入向后兼容的 `.pkmbackup.json`（含月度预算字段，支持覆盖或去重合并），学习小屋同样提供独立 JSON 备份。

---

## 快速开始与构建测试

### 1. Android 客户端构建与测试（Windows PowerShell）

```powershell
# 运行全部本地单元测试、编译测试代码并打包 Debug APK
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestSources assembleDebug --console=plain

# 连接模拟器或真机运行 UI 与 Room 迁移集成测试
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

Debug 安装包输出路径：`app/build/outputs/apk/debug/app-debug.apk`。

### 2. 启动本地云同步服务端（Spring Boot + MySQL 8.4）

1. 在本地 MySQL 8.4 执行 [server/src/main/resources/sql/schema.sql](server/src/main/resources/sql/schema.sql) 初始化 `pkmapp` 数据库（连接参数可通过 `PKM_DB_URL`、`PKM_DB_USER`、`PKM_DB_PASSWORD` 环境变量覆盖）。
2. 运行集成测试并打包启动：

```powershell
# 实库运行后端全链路集成测试
mvn -f server\pom.xml test

# 打包并启动服务端（默认监听 http://127.0.0.1:8080）
mvn -f server\pom.xml package -DskipTests
java -jar server\target\songguo-server-4.0.0.jar
```

3. 客户端联调地址：
   - **Android 模拟器**：在同步设置中填写 `http://10.0.2.2:8080`。
   - **USB 真机调试**：执行 `adb reverse tcp:8080 tcp:8080` 后，在手机端填写 `http://127.0.0.1:8080`。

### 3. Release 混淆打包与签名

Release 构建已默认开启 R8 代码混淆与资源压缩（`isMinifyEnabled = true`、`isShrinkResources = true`）。在项目根目录 `local.properties`（或环境变量 `PKM_STORE_FILE`、`PKM_STORE_PASSWORD`、`PKM_KEY_ALIAS`、`PKM_KEY_PASSWORD`）配置签名信息后执行：

```powershell
.\gradlew.bat assembleRelease
```

---

## 版本演进

### `v5.0.0`（对比 `v4.0.0`）
- **新增**：
  - **账本月度预算与双向云同步**：Room 升级至 `v3`（自动无损迁移 `MIGRATION_2_3`），客户端账本模型、本地 JSON 备份与服务端 `biz_ledgers` 同步支持月度预算字段；明细页新增报刊发丝线月度预算进度条（支持本月及历史月份对比复盘，80% 暖色预警、超支红字提醒）。
  - **后台静默防抖云同步**：接入 `SyncWorker` 5 秒防抖调度，在已登录且配置云端服务时，记账、改预算、攒钱、借还或专注完成后自动静默同步（游客与离线状态静默跳过）。
  - **考期倒计时**：复用学习计划结束日，在专注页与首页「今日速览」展示剩余天数倒计时。
  - **专注森林白噪音引擎**：新增原生 `AudioTrack` 实时合成环境音（林间微雨 / 木屋炉火 / 林梢清风），零额外音频体积占用，随专注计时自动启停，支持 5 秒试听与音量调节。
  - **暖纸风双空间桌面小组件**：新增「专注自习」与「生活预算」两个可缩放桌面小组件，支持免开屏直达专注页、记一笔或明细页，并在数据写入后实时刷新。
- **修改与优化**：
  - **生活小屋弹窗与键盘避让统一**：新增 `ForestSheetHelper`，将明细页账本选择、新建账本、月份选择、预算设置及删除确认统一为暖瓷白抽屉或无套框弹窗，并为明细与借钱输入表单补齐 Android 15/16 软键盘避让。
  - **攒钱心愿卡精简**：收起卡片内常驻展开的多层输入与操作框，改为清爽进度展示 + 底部「＋ 存入」与「编辑」轻量抽屉交互。
  - **图表页顶栏与趋势卡重构**：精简「支出 / 收入」与「周 / 月 / 年」顶栏布局，将时间翻页与年月选择收纳入趋势卡顶栏，点击趋势卡右上角水彩伙伴即可直接切换收支视图。
  - **锁屏专注通知增强**：专注计时通知补齐实时进度条与完成百分比文案，前台服务刷新节奏提升至 15 秒。
  - **服务端自动表结构补列**：新增 `SchemaMigrationRunner`，服务端启动时自动无损检查并补齐 `biz_ledgers.monthly_budget_cents` 字段。

### `v4.0.0`（对比 `v3.0.0`）
- **新增**：
  - **多账号登录门禁与游客模式**：新增中文分步登录注册页（`AuthActivity`）、账号快速切换、游客体验及登录后本地游客数据合并引导，并加入可动小松鼠吉祥物（`SquirrelMascotView`）与全站统一加载动效（`ForestLoadingView`）。
  - **个人侧边栏、小金库与意见反馈**：首页顶栏新增头像胶囊入口以拉出暖瓷白个人侧边栏；新增独立的「小金库（`VaultFragment`）」资产管理页与「意见反馈中心（`FeedbackFragment`）」。
  - **Spring Boot 3 + MySQL 8.4 云同步服务端（`server/`）**：提供 17 张表完整建表脚本、HS256 JWT + BCrypt 鉴权与刷新令牌轮换、12 张业务表增量推拉同步接口以及意见反馈管理接口。
  - **专注前通知权限引导与锁屏实时时长**：开始专注前引导开启通知权限，在锁屏与通知栏实时展示已学习时长。
- **修改与优化**：
  - **品牌与导航布局重构**：应用正式更名为 **小松果**；生活小屋底部导航收口为对称 4 格（明细 / 图表 / 攒钱 / 小金库）+ 右下悬浮「＋ 记一笔」按钮，取代旧版 `ProfileFragment`。
  - **存储升级至 Room `v2` 与安全加固**：全部 12 张业务表升级为 `ownerId` 复合主键并增加同步元字段（自动无损迁移 `MIGRATION_1_2`）；采用 PBKDF2 密码哈希与 Android Keystore AES-256-GCM 加密会话存储，修复离线账号升云与全角 URL 规范化问题。
  - **专注页、科目库与全局弹窗去套框重做**：专注卡片升级为发丝线时长选择条与原地防闪刷计时更新；科目库优化为轻盈双标签并支持选完科目直达计划书架；重构全局 `ForestDialogBuilder`，去除多层方框嵌套并支持长按钮自动堆叠防截断。

### `v3.0.0`（对比 `v2.0.0`）
- **新增**：
  - **Kotlin 混编与现代存储底座**：接入 Kotlin 2.2、KSP、Coroutines、`StateFlow`、`AppDatabase`（Room SQLite 12 张规范化表）与 `AppPreferences`（Preferences DataStore），内置旧版 `SharedPreferences` 单事务无损迁移器及迁移前冷备机制。
  - **后台保活与提醒兜底**：新增专注前台服务 `FocusTimerService` 保障熄屏与切后台精准计时，接入 `WorkManager` 周期重排学习与待办截止提醒。
  - **Release 混淆压缩工程化**：启用 R8 代码混淆与资源压缩（APK 体积缩减约 30%），并支持外部配置文件与环境变量签名。
- **修改与优化**：
  - **巨类拆解与双空间 MVVM 统一**：将原先超 4,000 行的学习主界面与近千行个人页拆分为职责单一的子页面与控制器，统一接入 `StudySharedViewModel`、`LifeSharedViewModel` 与 `ExchangeViewModel`，淘汰裸线程池。
  - **长列表性能升级**：学习待办草稿本、学习记录搜索、借钱按日流水及 24 币种汇率列表全面迁移至 `RecyclerView + ListAdapter + DiffUtil`。
