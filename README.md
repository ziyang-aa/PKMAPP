# 芽叶记（PKMAPP）

> **当前版本：`v3.0.0`**（基于 Java 11 + Kotlin 2.2 混编、双空间 MVVM + `StateFlow`、Jetpack Room + DataStore、`RecyclerView` 差分列表与前台服务保活架构）

静奢纸感 × 手绘蜡笔森林绘本风格的个人**学习 + 生活记账**双空间 Android 应用，完整保留 14 个自绘 `Canvas` 组件，无 Jetpack Compose 依赖，全量本地离线存储。

---

## v3.0.0 架构重构与更新说明（相对 v2.0.0-stable）

1. **构建与 Kotlin 混编底座**：接入 AGP `9.1.1` 内置 Kotlin（`KGP 2.2.10`）、`KSP 2.3.4`、`Lifecycle 2.9.4` 与 `Coroutines 1.10.2`，零退化复用全部 14 个 Java 自绘 `Canvas` 视图。
2. **巨类拆解与双空间 MVVM 统一**：移除 4,088 行 `StudyFragment.java` 与 970 行 `ProfileFragment.java`，拆解为职责单一的子 Fragment 与弹窗/视图控制器（单文件均 `< 500` 行）；新增 `StudySharedViewModel`、`LifeSharedViewModel` 与 `ExchangeViewModel`（`StateFlow` 驱动），淘汰裸线程池并统一为生命周期感知协程 IO（含 SAF 备份导入/导出异步防中断保护）。
3. **Room + Preferences DataStore 存储升级与无损迁移**：新增 `AppDatabase`（12 张规范化 SQLite 表，自动导出 `app/schemas/`）与 `AppPreferences` DataStore；内置 `LegacySharedPrefsMigrator` 幂等迁移器，升级前自动冷备原始 JSON 至 `filesDir/migration_backup_v1/` 再单事务无损迁入全部 12 份旧版 `SharedPreferences`，完全兼容旧版 `.pkmbackup.json` 导入导出。
4. **长列表 `RecyclerView + ListAdapter + DiffUtil` 性能优化**：学习待办草稿本（`StudyTodoListAdapter` + 独立驻留底部快记栏）、学习记录搜索抽屉（`StudyRecordListAdapter`）、借钱按日流水（`BorrowingListAdapter`）及 24 币种汇率速览/搜索弹窗（`ExchangeMultiRateAdapter` / `ExchangePickerAdapter`）全部升级为差分回收列表。
5. **专注前台服务保活与 `WorkManager` 提醒兜底**：新增 `FocusTimerService`（`FOREGROUND_SERVICE_SPECIAL_USE`）保障锁屏/切后台精准计时与通知栏暂停/继续/结束；接入 `WorkManager 2.10.5`（`ReminderRescheduleWorker` 每 6 小时自动兜底重排学习与待办截止提醒）。
6. **Release R8 混淆、资源压缩与签名工程化**：开启 `isMinifyEnabled = true` 与 `isShrinkResources = true`（安装包体积由 `28.3 MB` 缩减至 `19.9 MB`，降幅约 30%），通过 `res/raw/keep.xml` 与 `proguard-rules.pro` 保护 `norm_type_*` 动态分类图标及 Room/WorkManager 反射入口，支持 `local.properties` / 环境变量配置正式签名。

---

## 核心功能

### 1. 森林门厅（Home）
- 手绘蜡笔双小屋全景入口：**学习小屋（松栗）** 与 **生活小屋（小刺猬与小芽）**。
- **今日速览**：展示今日专注时长、待办事项数与本月支出，点击直达对应页面。
- 支持「记住上次进入的小屋」叶片开关；若存在进行中/暂停中的专注会话，顶部显示快捷续接入口。

### 2. 学习小屋（Study Space）
- **专注（森林自习室）**：支持多学习计划（考研 / 期末 / 自定义科目书架）、正计时 / 倒计时切换、自定义专注时长、屏幕常亮与通知栏后台控时，结束时可修正时长/科目并记录学习手记与下次续接点。
- **待办（独立草稿本）**：与学习计划完全解耦，采用互斥三分区 **「今天（逾期置顶 + 今日）/ 计划（未来按日 + 随时可做）/ 已完成（按实际完成日期与时刻倒序日志）」**，内置自绘瓷白日历**按日期筛选**（带待办/逾期/已完成三色圆点标记与相邻日翻页）、草稿本底部快记以及独立的截止通知提醒。
- **学习足迹（统计）**：累计专注摘要（次数/天数/日均/连续打卡）、科目分布（环形占比点按高亮 / 科目时长条形图 / 每日趋势）、6 周森林专注热力图、科目汇总与历史手记搜索/补记/待回顾。
- **我的小林地（成长花园）**：每累计专注 25 分钟获得 1 水滴，每日首次达标获得 1 种子（也可用 6 水滴兑换 1 种子）；浇灌 100 次水滴养成一棵手绘蜡笔成树并永久收录进林地收藏。

### 3. 生活小屋（Life Space）
- **明细**：多账本切换、本月收入/支出/结余统计、按日期分组的交易流水，内嵌**汇率换算**与**借钱统计**快捷入口。
- **图表**：周 / 月 / 年收支趋势折线图与分类占比排行榜。
- **记一笔**：23+1 项支出分类与 10+1 项收入分类（支持自定义分类）、四则计算器、日期选择与借贷自动联动。
- **攒钱**：创建多个攒钱目标，记录存入进度与幼芽成长阶段。
- **我的（森林档案）**：头像裁剪、昵称编辑、每日签到、总资产快照与账单 JSON 导出/导入。

---

## 项目结构

```text
PKMAPP/
├─ README.md                                  # 项目说明与结构导航
├─ AGENTS.md                                  # 项目 Agent 工程与设计总规范（反多纯色卡片、三图层等）
├─ DESIGN.md                                  # 芽叶记视觉与交互设计规范（静奢纸感体系）
├─ docs/
│  ├─ REFACTORING-PLAN.md                     # 长期维护与渐进式重构执行计划（Agent 可执行版）
│  ├─ REFACTORING-CHANGELOG.md                # 重构执行变动记录（相对计划的偏离与理由）
│  └─ WORKLOG-2026-11-22.md                   # 阶段性改版工作日志
├─ build.gradle.kts                           # 根工程 Gradle 配置
├─ settings.gradle.kts                        # 模块与仓库配置
├─ gradle.properties                          # Gradle 构建参数
├─ gradlew / gradlew.bat                      # Gradle Wrapper 脚本
├─ gradle/                                    # Wrapper 与依赖版本目录
└─ app/
   ├─ build.gradle.kts                        # Android App 模块构建配置
   ├─ proguard-rules.pro                      # 混淆规则
   └─ src/
      ├─ main/
      │  ├─ AndroidManifest.xml               # 组件、通知与权限声明
      │  ├─ java/com/example/pkmapp/
      │  │  ├─ SplashActivity.java            # 手绘蜡笔启动过渡页
      │  │  ├─ SplashScreenTiming.java        # 启动页展示时长控制
      │  │  ├─ MainActivity.java              # 单 Activity 容器、双空间切换与底部磨砂导航
      │  │  ├─ navigation/                    # 全局页面枚举（AppDestination）
      │  │  ├─ home/                          # 森林门厅（HomeFragment、PanoramaSizing）
      │  │  ├─ forest/                        # 公共森林组件库（设计系统底座）
      │  │  │  ├─ ForestUi.java               # 通用排版、按钮、无框叶片开关与复选框工厂
      │  │  │  ├─ ForestCalendarView.java     # 自绘瓷白纸感日历（含三色待办圆点标记）
      │  │  │  ├─ ForestSegmentedControl.java # 弹性滑动胶囊分段栏
      │  │  │  ├─ ForestNotebookPad.java      # 手工草稿纸待办容器
      │  │  │  ├─ ForestTodoRow.java          # 笔绘打勾与划线动效待办行
      │  │  │  ├─ ForestSceneView.java        # 首页双小屋全景画布
      │  │  │  ├─ FrostedNavigationBar.java   # 底部悬浮磨砂导航栏
      │  │  │  ├─ CrayonCard.java             # 暖瓷纸感卡片背景
      │  │  │  ├─ StudyBackdrop.java          # 森林纸纹与落叶背景
      │  │  │  └─ ForestButton / ForestSurface / ForestMotion / ForestBitmaps / ForestDialogBuilder
      │  │  ├─ study/                         # 学习小屋核心业务、MVVM 状态与自绘视图
      │  │  │  ├─ BaseStudyFragment.kt        # 学习小屋四页公共基类（三图层通栏、顶栏、弹窗与备份协调）
      │  │  │  ├─ StudySharedViewModel.kt     # 学习与待办共享 ViewModel（StateFlow 状态快照与跨页筛选协调）
      │  │  │  ├─ StudyFocusFragment.kt       # 专注（森林自习室）子页控制器（含原地防闪刷计时同步）
      │  │  │  ├─ StudyTodoFragment.kt        # 待办（独立草稿本）子页控制器
      │  │  │  ├─ StudyStatsFragment.kt       # 学习足迹（统计与热力图）子页控制器
      │  │  │  ├─ StudyGardenFragment.kt      # 我的小林地（成长花园）子页控制器
      │  │  │  ├─ StudyFocusTimerCard / StudyFocusDialogs / StudyAgendaDialogs # 专注卡片与日程/目标弹窗
      │  │  │  ├─ StudyTodoDialogs / StudyTodoRows / StudyStatsDialogs         # 待办与足迹弹窗及列表行构建器
│  │  │  ├─ StudyTodoListAdapter / StudyRecordListAdapter / FocusTimerService # 差分列表适配器与前台计时服务
      │  │  │  ├─ StudySheet.java             # 多层级返回栈瓷白底部弹窗框架
      │  │  │  ├─ StudyPlanDialogs.java       # 学习计划与科目书架管理弹窗
      │  │  │  ├─ StudyWorkDialogs.java       # 学习记录补记、回顾与计划事项表单
      │  │  │  ├─ StudyClockFace.java         # 极简横向渐变进度条时钟组件
      │  │  │  ├─ StudyChartView.java         # 环形占比（可点按）/ 科目条形 / 每日趋势自绘图
      │  │  │  ├─ StudyHeatmapView.java       # 近 6 周每日专注热力图
      │  │  │  ├─ StudyGardenView.java        # 手绘蜡笔小树生长与浇水动画场景
      │  │  │  ├─ StudyGrowth / StudyGrowthProgress / StudyNotebook / StudyStore
      │  │  │  └─ StudyPlan / StudySubject / StudyCatalog / StudyWork / StudyFormat / StudyNotifications
      │  │  ├─ todo/                          # 独立待办数据层与截止提醒通知
      │  │  │  ├─ TodoStore.java              # 独立待办持久化与排序
      │  │  │  └─ TodoNotifications.java      # 截止前提醒闹钟与测试通知
      │  │  ├─ core/                          # 现代化存储底座（Room SQLite + Preferences DataStore）
      │  │  │  ├─ database/                   # AppDatabase（12 张规范化表、DAO）、LegacySharedPrefsMigrator 无损迁移器
      │  │  │  └─ datastore/                  # AppPreferences（统一管理档案、门厅、专注与待办提醒偏好）
      │  │  ├─ life/                          # 生活小屋共享 MVVM 状态层（LifeSharedViewModel、LifeSnapshot）
      │  │  ├─ data/                          # 生活记账核心数据模型与 Room 持久化桥接（Ledger、Transaction、AssetStore）
      │  │  ├─ details/                       # 明细页与账本列表适配器
      │  │  ├─ record/                        # 记一笔页面、分类图标解析（CategoryIconResolver）与计算器
      │  │  ├─ charts/                        # 收支趋势折线图与分类排行
      │  │  ├─ savings/                       # 攒钱目标页面与存储
      │  │  ├─ borrowing/                     # 借入/借出统计、自定义日历与账本联动
      │  │  ├─ exchange/                      # 实时汇率拉取、缓存与换算（ExchangeViewModel、ExchangeRateService）
      │  │  ├─ profile/                       # 森林档案（ProfileFragment.kt 及头像裁剪/备份/账本分类/签到控制器）
      │  │  └─ backup/                        # 账单 JSON 导入导出与去重合并
      │  └─ res/
      │     ├─ layout/                        # 25 个 XML 页面与列表项布局
      │     ├─ drawable/                      # 矢量图标、卡片圆角与按钮状态样式
      │     ├─ drawable-nodpi/                # 手绘蜡笔插画、分类图标（norm_type_*）与纸纹图
      │     ├─ mipmap-*/                      # 应用桌面图标
      │     ├─ color/ & values/               # 色彩 Token、尺寸、样式与主题定义
      │     └─ anim/ & xml/                   # 转场动画与备份规则
      ├─ schemas/                             # Room 自动导出的数据库版本 Schema JSON
      ├─ test/                                # 38 个 JVM 本地单元测试文件（123 条用例，无需设备）
      └─ androidTest/                         # 7 个真机/模拟器 UI 与集成测试
```

---

## 数据存储与备份

所有数据均保存在设备本地，不依赖云端账号：

- **本地结构化存储与平滑升级**：账本、交易、借还记录、攒钱目标、自定义分类、学习计划/科目/记录/事项/回顾/林地与独立待办统一持久化于 **Jetpack Room（SQLite `pkm_app.db`）**，个人档案与界面偏好存储于 **Jetpack Preferences DataStore**；旧版 `SharedPreferences` 升级时由 `LegacySharedPrefsMigrator` 自动先落盘原始 JSON 冷备（`filesDir/migration_backup_v1/`）再单事务无损迁入。
- **生活记账备份**：可在 **「生活小屋 → 我的 → 导出/导入账单」** 保存或恢复向后兼容的 `.pkmbackup.json`（支持完全覆盖或按「时间 + 金额 + 备注」去重合并）。
- **学习备份**：可在 **「学习小屋 → 当前计划 → 学习备份」** 导出或合并恢复 JSON 备份。

---

## 构建与测试

在项目根目录（Windows PowerShell）执行编译与全部本地单元测试：

```powershell
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestSources assembleDebug
```

连接 Android 模拟器或真机后，可执行 UI 集成测试：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

生成的调试安装包位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Release 混淆打包与签名

Release 构建已启用 R8 代码混淆压缩（`isMinifyEnabled = true`）与资源压缩（`isShrinkResources = true`）。在项目根目录 `local.properties`（或环境变量 `PKM_STORE_FILE`、`PKM_STORE_PASSWORD`、`PKM_KEY_ALIAS`、`PKM_KEY_PASSWORD`）中配置个人正式签名（切勿提交 `.jks` / `.keystore` 入库）：

```properties
release.storeFile=path/to/your-release.jks
release.storePassword=...
release.keyAlias=...
release.keyPassword=...
```

随后执行正式包构建（本地未放正式 keystore 时，可附加 `-PuseDebugSigningForRelease=true` 供模拟器冒烟验证）：

```powershell
.\gradlew.bat assembleRelease
```
