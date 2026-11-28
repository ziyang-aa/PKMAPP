# 芽叶记（PKMAPP）长期演进与重构总计划（Agent 可执行版）

> **文档定位**：本计划是一份面向 AI Coding Agent 与开发者的**可直接执行的分阶段重构蓝图**。执行重构前，请先通读根目录 [AGENTS.md](../AGENTS.md)、[DESIGN.md](../DESIGN.md) 与 [README.md](../README.md)，再严格按本计划的 Phase 0 → Phase 6 顺序推进。每完成一个 Phase 都必须通过全部编译、单测与实机验收门禁，严禁跳步或推倒重写。执行中的偏离与理由见 [REFACTORING-CHANGELOG.md](REFACTORING-CHANGELOG.md)。

---

## 0. 执行前提与不可破坏红线（Invariants）

### 0.1 产品与视觉红线（不得在重构中丢失）
1. **静谧奢华暖纸手账气质（Quiet-Luxury Warm Paper & Editorial Hairline）**：
   - 严禁回退为多纯色卡片堆砌、卡片套卡片或弹窗套卡片。
   - 工具栏优先使用报刊发丝线（`0.75dp` `0x1f5c4938`）+ 无框文字/细线图标（`ic_editorial_*`）+ 暖瓷白细描边胶囊（`#FFFEFA`），避免厚重纯色按钮滥用与 `layout_weight="1"` 拉伸短文本。
2. **严格三图层 Edge-to-Edge**：
   - Layer 1：根布局全屏通栏背景（`bg_fixed_forest_backdrop` / `StudyBackdrop`），根布局 `paddingTop = 0`、`paddingBottom = 0`。
   - Layer 2：`fragment_container` 保持 `topMargin = 0`，内部滚动容器 `clipToPadding = false`，通过 `bars.top` / `bars.bottom` 内边距避免顶部状态栏与底部导航栏割裂。
   - Layer 3：悬浮毛玻璃导航栏（`FrostedNavigationBar`）。
3. **顶栏唯一标准**：全部一级页（生活小屋 + 学习小屋）顶栏统一为「左侧 `56dp × 56dp` `return_home_door` + 右侧仅放 `24sp` 粗体标题」，不在右上角塞入缩小按钮。
4. **自绘组件资产必须直接复用，严禁重写退化**：
   - 全部 14 个核心自绘视图均已实机打磨成型，重构期间**保持原有 Java 实现直接复用**（Kotlin 与 Compose 均可无缝调用/`AndroidView` 嵌入）：
     - `forest/`: `ForestCalendarView`、`ForestNotebookPad`、`ForestSegmentedControl`、`ForestSceneView`、`FrostedNavigationBar`、`CrayonCard`、`StudyBackdrop`、`ForestButton`
     - `study/`: `StudyClockFace`、`StudyChartView`、`StudyHeatmapView`、`StudyGardenView`、`StudyGrowthProgress`
     - `charts/` & `profile/`: `TrendChartView`、`AvatarCropView`
5. **动态资源与测试契约保护**：
   - `CategoryIconResolver` 通过 `Resources.getIdentifier("norm_type_...", ...)` 动态加载图标，**禁止误删 `norm_type_*` 资源**。
   - 必须保留全部既有 UI 测试契约（见第 1.4 节）以及无闪屏的原地计时器更新（`syncRunningStateInPlace`）。
   - 严禁调用系统原生 `MaterialDatePicker` / `DatePickerDialog`，统一复用 `ForestCalendarView`。
6. **零数据丢失承诺**：
   - 本项目将作为长期自用 App 维护，重构期间任何存储升级（如迁入 Room）必须提供**幂等的一次性无损迁移器 + 迁移前快照保留**，确保用户已有记账流水、借钱记录、攒钱目标、学习计划、专注记录与待办一条不丢。

---

## 1. 当前项目全景 X 光（基线事实）

### 1.1 构建与工程基线
- **构建工具**：AGP `9.1.1`，Gradle Wrapper，Version Catalog (`gradle/libs.versions.toml`)
- **Android SDK**：`compileSdk 36`（minor 1），`minSdk 24`，`targetSdk 36`
- **当前语言与编译**：Java 11 为主（`viewBinding = true`）；Phase 1 起通过 AGP 9 内置 Kotlin（KGP 2.2.10）支持 Java/Kotlin 混编。
- **当前运行依赖（极精简，共 4 个）**：`appcompat:1.6.1`、`material:1.10.0`、`fragment:1.8.9`、`recyclerview:1.4.0`
- **源码体量**：`95` 个 Java 文件，共 `21,077` 行代码；`31` 个 JVM 单元测试文件；`7` 个 Espresso 仪表测试文件
- **启动入口**：`.SplashActivity`（exported），`.MainActivity`（非 exported，单 Activity + Fragment 容器）

### 1.2 包结构与代码热点地图（按维护痛点排序）

| 包路径 (`com.example.pkmapp.*`) | 文件数 | 总规模/核心大文件 | 主要职责与当前重构痛点 |
| --- | --- | --- | --- |
| `study/` | 18 | **`StudyFragment.java` (4,088 行)**<br>`StudyNotebook.java` (922 行)<br>`StudyPlanDialogs.java` (742 行)<br>`StudyWorkDialogs.java` (500 行)<br>`StudySheet.java` (471 行) | **【最大痛点】** `StudyFragment` 一文件承载 4 个完整页面（专注/待办/足迹/林地）与全部弹窗、计时器、热力图编排，占全库近 20% 代码量；直接操作单例 `StudyStore` 与 `TodoStore` |
| `profile/` | 5 | **`ProfileFragment.java` (970 行)**<br>`AvatarCropView.java` (246 行) | 承担头像裁剪、资产弹窗、分类/账本管理、签到、导入导出，职责过重 |
| `forest/` | 13 | `ForestUi.java` (711 行) + 12 个公共组件 | 视觉设计系统与自绘控件底座；稳定度高，重构时保持向后兼容，可逐步沉淀至 `core/ui` |
| `exchange/` | 5 | `ExchangeFragment.java` (585 行)<br>`ExchangeRateService.java` (199 行) | 24 种 ECB 货币批量换算 + 本地缓存；结构已较清晰，待接入 `ViewModel` 与 `RecyclerView` |
| `home/` | 2 | `HomeFragment.java` (517 行) | 森林双门入口、今日速览、记住上次小屋开关；跨空间聚合直接查 3 个单例 |
| `borrowing/` | 10 | `BorrowingFragment.java` (506 行) + 9 个领域类 | 领域模型切分良好，仅 UI 层仍为动态 `addView` 与直连 Store |
| `record/` | 8 | `RecordFragment.java` (499 行) + 计算器/分类/解析 | 逻辑拆分良好（`CalculatorEngine`、`MoneyParser` 等均有单测） |
| `charts/` | 3 | `ChartsFragment.java` (494 行)<br>`ChartDataCalculator.java` (263 行)<br>`TrendChartView.java` (235 行) | 图表计算已独立，Fragment 需迁入 `ViewModel` 驱动 |
| `details/` | 4 | `DetailsFragment.java` (384 行)<br>`TransactionListAdapter.java` (157 行) | 明细页已使用 `RecyclerView`，表现良好，适合作为 MVVM 改造样板页 |
| `data/` | 11 | `InMemoryLedgerRepository.java` (361 行)<br>`LedgerCodec.java` (125 行)<br>`AssetStore.java` (93 行) | 生活账本核心仓库，底层绑定 `SharedPreferencesLedgerPersistence`，需演进为 Room 驱动的异步仓库 |
| `backup/` | 6 | `BackupMerger.java` (225 行)<br>`BackupCodec.java` (208 行) | 账单 + 借钱 JSON 备份导出与去重合并；迁移 Room 后必须继续保持 JSON 格式向后兼容 |
| `todo/` | 2 | `TodoStore.java` (345 行)<br>`TodoNotifications.java` (158 行) | 独立待办持久化与截止闹钟；需迁入 Room 并保留三分区排序语义 |
| `savings/` | 3 | `SavingsFragment.java` (225 行)<br>`SavingsGoalStore.java` (134 行) | 攒钱目标管理，结构简洁 |
| `(root)` & `navigation/` | 4 | `MainActivity.java` (341 行)<br>`SplashActivity.java` (55 行)<br>`AppDestination.java` (31 行) | 单 Activity 导航、三图层 Insets 与双底栏管理 |

### 1.3 全量持久化现状清单（12 份 `SharedPreferences` — Room 迁移对照表）

执行 Phase 4（存储升级）时，必须逐项核对下表，**不得遗漏任何一份既有文件**：

| # | `SharedPreferences` 文件名 | 关键 Key | 当前归属类 | 目标去向 | 说明 |
| --- | --- | --- | --- | --- | --- |
| 1 | `ledger_data` | `state` (整份 JSON) | `SharedPreferencesLedgerPersistence` | **→ Room** (`ledgers`, `transactions`) | 账本与收支流水核心资产 |
| 2 | `borrowing_records` | `records` (JSON 数组) | `BorrowingRecordStore` | **→ Room** (`borrowing_records`) | 借出/借入往来，关联 `ledgerId` 与 `sourceTxId` |
| 3 | `savings_goals` | `goals` (JSON 数组) | `SavingsGoalStore` | **→ Room** (`savings_goals`) | 攒钱目标与当前进度 |
| 4 | `forest_study_v1` | `notebook` (整份 JSON) | `StudyStore` | **→ Room** (`study_plans`, `study_subjects`, `study_records`, `study_tasks`) + 轻量状态 | 包含学习计划、科目、专注记录、水滴/种子/成长值、活跃计时会话 |
| 5 | `forest_todo_v1` | `data` (整份 JSON) | `TodoStore` | **→ Room** (`todo_items`) + 提醒偏好迁 DataStore | 独立待办条目 + `remindEnabled` / `defaultRemindTime` |
| 6 | `forest_assets` | `initial_assets_<id>`, `liabilities_<id>` | `AssetStore` | **→ Room** (`ledger_assets` 或并入 `ledgers` 表字段) | 按账本 ID 存储初始资产与负债 |
| 7 | `record_categories` | `custom_expense/income`, `deleted_expense/income` | `CategoryStore` | **→ Room** (`custom_categories`) 或保留 DataStore | 自定义/隐藏收支分类 |
| 8 | `forest_profile` | 昵称、打卡天数、签到日期集合等 | `ProfileFragment` | **→ Preferences DataStore** | 轻量个人档案与每日签到状态 |
| 9 | `forest_home_prefs` | `remember_last_cottage`, `last_cottage` | `HomeFragment` | **→ Preferences DataStore** | 首页双门记忆开关 |
| 10 | `exchange_rates` | 汇率对与批量缓存 JSON + 时间戳 | `ExchangeRateService` | **保留（或迁 DataStore）** | 纯网络缓存，可随时从 Frankfurter v2 重拉 |
| 11 | `forest_study_ui` & `study_ui` | `countdown_mode`, `keep_screen_on`, `askedNotifications` | `StudyFragment` | **→ Preferences DataStore** | 专注页 UI 偏好开关 |
| 12 | `study_notifications` & `todo_notifications` | `deadlineAlarm`, `nextAlarm` | `StudyNotifications`, `TodoNotifications` | **保留（轻量调度标记）** | 闹钟去重时间戳 |

### 1.4 自动化测试保护清单（重构不可破坏的锚点）
- **31 个 JVM 单元测试**（`app/src/test/...`）：覆盖 `BackupCodec/Merger`、`Borrowing*`、`ChartDataCalculator`、`InMemoryLedgerRepository`、`ExchangeRateJsonParser/Calculator`、`CalculatorEngine`、`CategoryIconResolver`、`MoneyParser`、`SavingsGoal`、`StudyNotebook`、`StudyWorkflow` 等。
- **7 个 Espresso UI 测试**（`app/src/androidTest/...`）：
  - `MainNavigationTest`：断言三图层全屏无割裂、`R.id.page_back_button`、`R.id.home_enter_life`
  - `StudyBackdropTest`：断言 `root.getPaddingTop() == 0`、`root.getHeight() == frame.getHeight()`、`!scroll.getClipToPadding()`、`R.id.page_back_button` 随页面滑入状态栏后方
  - `StudyNavigationTest`：断言 `R.id.home_enter_study`、`R.id.home_resume_study`、`R.id.nav_study_garden`、`R.id.study_wallet`、`R.id.study_water_button`
  - `StudyPlanFlowTest`：按精确文案点击 `"当前计划 · 408 考研 ▾"`、`"当前计划 · 大三上期末 ▾"`、`"新建计划"`、`"完成当前计划"`、`"归档并新建"`
  - `StudyWorkflowUiTest`：按精确文案点击 `"补记学习"`，断言 `R.id.study_date_today/week/month`

---

## 2. 长期目标技术栈与目录规划

### 2.1 目标技术栈（渐进式演进，零推倒重写）
- **语言**：**Kotlin 优先（Java 11 + Kotlin 2.x 100% 混编）**。存量稳定的算法/解析/自绘 `Canvas` 类保持 Java 不动；所有拆分出来的 Fragment、ViewModel、Repository、Room Entity/DAO 与新功能一律使用 Kotlin。
- **UI 路线**：**以原生 View + ViewBinding + 自绘 Canvas 为稳定底座，新功能页按需引入 Jetpack Compose（通过 `ComposeView` + `AndroidView` 复用既有自绘控件）**。
  > 说明：Phase 0–5 均不强制重写既有 UI 到 Compose，先把架构分层、数据持久化、后台保活彻底夯实；Phase 6 及之后的新功能页（如年度回顾、WebDAV 同步设置）可直接采用 Compose。
- **架构与异步**：单向数据流 MVVM —— `ViewModel` + Kotlin `Coroutines` + `StateFlow`（Java 侧调用处通过轻量桥接或 `LiveData` 兼容）。
- **持久化**：**Jetpack Room（SQLite + KSP 注解处理 + 导出 Schema JSON + 自动迁移测试）** + **Jetpack DataStore（Preferences）**。
- **后台与提醒**：**Foreground Service**（专注计时常驻通知保活）+ **WorkManager**（定时提醒重调度与自动备份）。
- **工程质量**：GitHub Actions CI + Release R8 混淆压缩（`isMinifyEnabled = true`, `isShrinkResources = true`）。

### 2.2 目标代码包结构（单模块内按 Feature 清晰分层，避免过早多模块膨胀）

```text
app/src/main/java/com/example/pkmapp/
├─ core/
│  ├─ database/          # AppDatabase (Room), DAOs, Entities, Migrations, LegacySharedPrefsMigrator
│  ├─ datastore/         # AppPreferences (Preferences DataStore)
│  └─ ui/                # 共用基类、Insets 扩展、UI State 公共类型（复用现有 forest/ 组件）
├─ forest/               # 保留：既有手绘组件与设计系统底座（ForestUi, ForestCalendarView 等）
├─ feature/
│  ├─ study/
│  │  ├─ BaseStudyFragment.kt
│  │  ├─ StudySharedViewModel.kt
│  │  ├─ focus/          # StudyFocusFragment.kt, FocusTimerService.kt
│  │  ├─ todo/           # StudyTodoFragment.kt, TodoViewModel.kt, TodoListAdapter.kt
│  │  ├─ stats/          # StudyStatsFragment.kt, StudyRecordListAdapter.kt
│  │  └─ garden/         # StudyGardenFragment.kt
│  └─ life/              # 渐进拆分 ProfileFragment 等并接入 LifeSharedViewModel
├─ study/                # 存量领域模型与弹窗（StudyNotebook, StudySheet 等逐步瘦身）
├─ data/ details/ charts/ record/ savings/ borrowing/ exchange/ profile/ backup/ home/ todo/
```

---

## 3. 分阶段可执行重构计划（Phase 0 → Phase 6）

> **执行铁律**：每个 Phase 结束后必须运行：
> ```powershell
> .\gradlew.bat testDebugUnitTest compileDebugAndroidTestSources assembleDebug --no-daemon --console=plain
> ```
> 全部通过并经人工/实机冒烟验证后，再进入下一个 Phase。

---

### Phase 0：锁定 v2.0 稳定基线、更新规则与接入 CI（预估 0.5 天）

**目标**：为重构建立「随时可回退的安全网」与自动化回归门禁。

**具体任务**：
1. **Git 里程碑**：在用户确认后，将当前稳定的纯 Java 版本提交，并创建只读里程碑标签 `v2.0.0-java-baseline`。
2. **更新 [AGENTS.md](../AGENTS.md) 第 1 节**：解除课程期的「严禁 Kotlin」限制，替换为长期演进规则：
   - 存量稳定模块保持 Java 兼容，禁止无意义的“为了改语言而重写”；
   - 新建模块、拆分出来的 Fragment/ViewModel/Repository/Room 层优先使用 **Kotlin + Coroutines + StateFlow**；
   - 继续严守 UI 反多纯色卡片、三图层通栏、顶栏规范与自绘日历规范。
3. **新增 GitHub Actions CI（`.github/workflows/android-ci.yml`）**：
   - 在 `push` / `pull_request` 时自动执行 `./gradlew testDebugUnitTest compileDebugAndroidTestSources assembleDebug --no-daemon`。

**验收标准**：
- 基线分支与 Tag 已确立；CI 配置语法正确；本地全量单测与构建全绿。

---

### Phase 1：开启 Kotlin + Lifecycle + Coroutines 基础设施（预估 0.5 天）

**目标**：最小化改造 Gradle 构建，零改动业务代码，验证 Java/Kotlin 混编编译链路畅通。

**涉及文件**：
- `gradle/libs.versions.toml`
- `build.gradle.kts`（根目录）
- `app/build.gradle.kts`

**具体任务**：
1. 在 `libs.versions.toml` 中引入与 AGP `9.1.1` 匹配的：
   - Kotlin 由 AGP 9 内置提供，不再单独声明或应用 `org.jetbrains.kotlin.android` 插件；`ksp` 插件仅在 catalog 中占位供后续 Phase 4 使用（当前不应用）
   - `androidx.lifecycle:lifecycle-viewmodel-ktx`、`lifecycle-runtime-ktx`、`lifecycle-livedata-ktx`
   - `org.jetbrains.kotlinx:kotlinx-coroutines-android`、`kotlinx-coroutines-test`（testImplementation）
   - `androidx.fragment:fragment-ktx`、`androidx.activity:activity-ktx`
2. 在 `app/build.gradle.kts` 中通过 `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }` 配置字节码目标版本（取代旧式 `kotlinOptions`），与现有 `JavaVersion.VERSION_11` 完全对齐。
3. 编写 1 个极简 Kotlin 单元测试（例如校验 `AppDestination` 或 `StudyPlan.Partner.parse` 互调），确认 Kotlin 测试与 Java 主代码 100% 互通。

**验收标准**：
- 既有 95 个 Java 文件零修改；全部 31+ 个单测与 `compileDebugAndroidTestSources` 全绿；Debug APK 可正常安装启动。

**执行状态**：已完成（2026-11-23），新增 `AppDestinationKotlinInteropTest.kt`。

---

### Phase 2：拆解 4,088 行 `StudyFragment` 单体巨类（最高优先级，预估 2–3 天）

**目标**：彻底解决全库最大维护痛点，将 `StudyFragment.java` 拆分为 4 个职责单一、高内聚的子页 + 共享 `StudySharedViewModel`，同时零回归保留全部视觉细节与测试契约。

**拆解蓝图**：
1. **抽取共享基础层**：
   - `BaseStudyFragment.kt`：统一承载三图层 `ScrollView` + `applyScrollInsets(bars.top / bars.bottom + 112dp)`、顶栏（`56dp` `R.id.page_back_button` + `24sp` 右对齐标题）、`StudySheet` 生命周期管理（`onDestroyView` 安全关闭）、`tintedIcon` 与 `bottomHairlineBg` 工具方法。
   - `StudySharedViewModel.kt`：封装 `StudyStore` 与 `TodoStore` 的读写，暴露 `StateFlow<StudyUiState>` 与 `StateFlow<TodoUiState>`，接管备份导入/导出协调与 `HISTORY_REQUEST` 跨页筛选状态（不再靠 `FragmentResultListener` 硬传）。
2. **拆分 4 个独立 Fragment**（按从简到繁顺序逐一迁出，每拆完一个即验证）：
   - `StudyGardenFragment.kt`（约 180 行）：承接 `garden()`、`care()`、`StudyGardenView` 动画生命周期与 `R.id.study_wallet` / `R.id.study_water_button`。
   - `StudyTodoFragment.kt`（约 380 行）：承接 `todoPage()`、报刊发丝线工具栏、互斥三分区（今天/计划/已完成）、单日筛选与 `ForestCalendarView` 弹窗、独立待办编辑/提醒弹窗。
   - `StudyStatsFragment.kt`（约 420 行）：承接 `statistics()`、无框计划筛选、短下划线周期页签（保留 `R.id.study_date_today/week/month`）、`StudyChartView`、`StudyHeatmapView` 与补记学习入口（保留 `"补记学习"` 文案）。
   - `StudyFocusFragment.kt`（约 480 行）：承接 `focus()`、报刊发丝线计划下拉（保留 `"当前计划 · <title> ▾"` 文案）、`StudyClockFace`、**原地更新防闪刷 `syncRunningStateInPlace`**、完成/放弃弹窗与每日目标弹窗。
3. **更新 `MainActivity.java` 路由工厂**：
   - `STUDY_FOCUS` → `StudyFocusFragment`
   - `STUDY_TODO` → `StudyTodoFragment`
   - `STUDY_STATS` → `StudyStatsFragment`
   - `STUDY_GARDEN` → `StudyGardenFragment`
   - 保留 `StudyFragment.fromNotification(...)` 迁移到 `StudyFocusFragment.fromNotification(...)`。
4. **删除原 `StudyFragment.java`**（或确认零引用后移除）。

**验收标准**：
- 4 个子 Fragment 单文件均控制在 `500` 行以内；
- `StudyBackdropTest`、`StudyNavigationTest`、`StudyPlanFlowTest`、`StudyWorkflowUiTest` 编译且逻辑契约全部满足；
- 实机验证 4 个子页切换、暂停/继续零闪屏、待办勾选/日期筛选、计划切换与伙伴联动完全正常。

**执行状态**：已完成（2026-11-24），`StudyFragment.java`（4,088 行）已完全移除并拆解为 `BaseStudyFragment.kt`、`StudySharedViewModel.kt`、4 个独立子页 Fragment（均 < 500 行）及配套弹窗/视图构建器，新增 `StudySharedViewModelTest.kt`（详见 [REFACTORING-CHANGELOG.md](REFACTORING-CHANGELOG.md)）。

---

### Phase 3：拆解 `ProfileFragment` 与生活小屋统一 MVVM（预估 1.5–2 天）

**目标**：消除生活小屋各页直接操作单例和 `onResume` 全页硬刷的问题，拆薄 970 行的 `ProfileFragment`。

**具体任务**：
1. **引入 `LifeSharedViewModel.kt`**：
   - 聚合 `InMemoryLedgerRepository`、`AssetStore`、`BorrowingRecordStore`、`SavingsGoalStore`、`CategoryStore` 的变更流，暴露 `StateFlow<LifeSnapshot>`。
   - 首页 `HomeFragment` 的「今日速览」订阅 `LifeSharedViewModel` + `StudySharedViewModel`，彻底消除返回首页数据滞后问题。
2. **拆解 `ProfileFragment.java`（970 行 → < 350 行）**：
   - 抽离 `AvatarCropSheetController.kt`（头像裁剪弹窗与 `AvatarStore` 读写）
   - 抽离 `AssetAndLedgerDialogs.kt`（资产/负债编辑、账本管理、分类管理弹窗）
   - 抽离 `BackupCoordinator.kt`（SAF 文档选择器 + `BackupManager` 导入导出状态管理）
3. **渐进接入生活小屋各页**：
   - `DetailsFragment`、`ChartsFragment`、`RecordFragment`、`SavingsFragment`、`BorrowingFragment`、`ExchangeFragment` 逐步改为观察 `LifeSharedViewModel` / 各自的 `ExchangeViewModel`，将网络请求与金额计算从 Fragment 移入 ViewModel。

**验收标准**：
- 记一笔 / 删除一笔 / 记一笔借钱后，明细、图表、我的总资产、首页今日速览自动同步更新；
- 全部生活小屋相关 JVM 单元测试全绿。

**执行状态**：已完成（2026-11-24），新增 `LifeSharedViewModel.kt` 与 `ExchangeViewModel.kt`，将 970 行 `ProfileFragment.java` 拆解为 138 行 `ProfileFragment.kt` 及 4 个职责组件（`AvatarCropSheetController.kt`、`BackupCoordinator.kt`、`AssetAndLedgerDialogs.kt`、`ProfileIdentityDialogs.kt`），生活小屋全部 7 个页面与 `HomeFragment` 今日速览已统一收敛至响应式 MVVM，新增 `LifeSharedViewModelTest.kt` 与 `ExchangeViewModelTest.kt`（详见 [REFACTORING-CHANGELOG.md](REFACTORING-CHANGELOG.md)）。

---

### Phase 4：存储层现代化 —— Jetpack Room + DataStore + 无损迁移（核心里程碑，预估 3–4 天）

**目标**：将业务流水从 `SharedPreferences` 整段 JSON 迁入结构化 SQLite（Room），奠定 3~5 年大数据量与复杂查询的底座，同时保证老版本覆盖升级**零数据丢失**。

**具体任务**：
1. **配置 Room + KSP + Schema 导出**：
   - 在 `app/build.gradle.kts` 配置 `ksp` 导出 Schema 到 `app/schemas/`（必须提交进 Git，用于未来数据库升级自动校验 `Migration`）。
2. **定义清晰的数据表（`AppDatabase`, `version = 1`）**：
   - `ledgers` (`id`, `name`, `initialAssetsCents`, `liabilitiesCents`, `createdAt`, `isDefault`)
   - `transactions` (`id`, `ledgerId`, `type`, `category`, `amountCents`, `occurredAt`, `note`, `createdAt`) — 建索引 `(ledgerId, occurredAt)`
   - `borrowing_records` (`id`, `ledgerId`, `direction`, `counterparty`, `amountCents`, `occurredAt`, `note`, `sourceTransactionId`, `settled`) — 建索引 `(ledgerId, occurredAt)`
   - `savings_goals` (`id`, `title`, `targetCents`, `savedCents`, `deadline`, `sortOrder`)
   - `custom_categories` (`id`, `type`, `name`, `isDeletedDefault`, `sortOrder`)
   - `study_plans` (`id`, `title`, `kind`, `due`, `partner`, `previousPlanId`, `createdAt`, `archivedAt`, `selectedSubjectId`, `sessionGoal`, `dailyGoal`, `remindDeadlines`, `remindTime`, `isCurrent`)
   - `study_subjects` (`id`, `planId`, `title`, `hint`, `due`, `color`, `sortOrder`) — 外键级联 `planId`
   - `study_records` (`id`, `planId`, `subjectId`, `subjectTitleSnapshot`, `subjectColorSnapshot`, `durationMillis`, `completedAt`, `note`, `nextHint`, `goalCompleted`) — 建索引 `(planId, completedAt)`
   - `study_tasks` (`id`, `planId`, `subjectId`, `title`, `due`, `doneAt`)
   - `study_garden_state`（单行表：`water`, `seeds`, `planted`, `growth`, `trees`, `carryMillis`, 活跃计时会话字段）
   - `todo_items` (`id`, `title`, `note`, `dueDate`, `dueTime`, `remindBeforeMinutes`, `createdAt`, `completedAt`, `sortOrder`) — 建索引 `(completedAt, dueDate)`
3. **偏好设置迁入 `Preferences DataStore`**：
   - 统一收口 `forest_profile`、`forest_home_prefs`、`forest_study_ui`、`study_ui` 与待办默认提醒开关/时间。
4. **编写幂等无损迁移器 `LegacySharedPrefsMigrator.kt`**：
   - 在 `Application` / 数据库首次打开时，检查 `legacy_migration_v1_done` 标记；
   - 若未迁移：读取全部旧 `SharedPreferences`（第 1.3 节 1–9 项），先导出完整原始 JSON 备份到 `filesDir/migration_backup_v1/` 作为兜底，再在单个 Room 事务（`withTransaction`）中写入全部表，成功后才写入完成标记；
   - **禁止**在首个升级版本直接清空旧 `SharedPreferences`（留作 1~2 个版本的冷备）。
5. **保持 `BackupCodec` / `BackupManager` 100% 兼容**：
   - 用户手动导出的 JSON 备份文件格式保持向后兼容，可导入旧版 v2.0 导出的备份文件，并扩展支持导出学习记录与待办（可选开关）。

**验收标准**：
- 为 `LegacySharedPrefsMigrator` 编写覆盖全表字段的单元测试（构造一份包含多账本、借钱、攒钱、已归档学习计划、计时中会话、逾期/已完成待办的旧版 JSON，验证迁入 Room 后条数与金额/时长 100% 一致）；
- 现有 `BackupCodecTest`、`BackupMergerTest`、`InMemoryLedgerRepositoryTest`、`StudyNotebookTest` 等全部通过；
- 模拟「先装旧版 v2.0 写测试数据 → 覆盖安装新版 APK」实机验证零丢数、零崩溃。

**执行状态**：已完成（2026-11-24），接入 `Room 2.8.4` + `KSP 2.3.4`（导出 `app/schemas/` `AppDatabase/1.json`）+ `Preferences DataStore 1.1.7`（`AppPreferences`），落地 12 张规范化 SQLite 表与幂等无损迁移器 `LegacySharedPrefsMigrator.kt`（自动冷备至 `filesDir/migration_backup_v1/` 且保留旧版 `SharedPreferences`），新增覆盖全表字段与旧版升级的 `LegacySharedPrefsMigratorTest.kt`（详见 [REFACTORING-CHANGELOG.md](REFACTORING-CHANGELOG.md)）。

---

### Phase 5：长列表性能优化与真机后台保活（预估 2 天）

**目标**：解决长期使用（1 年以上数据累积）后的滑动卡顿与真机锁屏掉计时痛点。

**具体任务**：
1. **长列表 `ScrollView + addView` → `RecyclerView + ListAdapter + DiffUtil`**：
   - `StudyTodoFragment`（待办列表，特别是「已完成」日期分组日志）
   - `StudyStatsFragment`（历史学习记录清单）
   - `BorrowingFragment`（按日分组借钱流水）
   - `ExchangeFragment`（24 种货币搜索弹窗与常用货币速览）
2. **专注计时器升级为前台服务 `FocusTimerService.kt`**：
   - 开始专注时启动 `ForegroundService`（声明 `FOREGROUND_SERVICE_SPECIAL_USE` / 对应 Android 14+ 类型与通知渠道），通知栏常驻显示当前科目与实时计时，支持通知栏直接点「暂停/继续/完成」；
   - 锁屏、切微信或系统内存吃紧时不再丢秒；UI 回到前台直接订阅 Service/ViewModel 的 `StateFlow`。
3. **提醒调度加固**：
   - 接入 `WorkManager` 定期兜底重排 `TodoNotifications` 与 `StudyNotifications`（应对开机重启、时区变更与系统清理）。

**验收标准**：
- 插入 500 条模拟账单 + 500 条待办 + 300 条学习记录，滑动帧率流畅无明显掉帧；
- 开启专注计时后退到后台锁屏 3 分钟再亮屏，计时精准连续且通知栏操作同步无误。

**执行状态**：已完成（2026-11-24），将 `StudyTodoFragment`（`StudyTodoListAdapter` + `NotebookRuleDecoration` + 持久化底部快记条）、`StudyStatsDialogs.showRecordsSheet`（`StudyRecordListAdapter`）、`BorrowingFragment`（`BorrowingListAdapter`）与 `ExchangeFragment`（`ExchangeMultiRateAdapter` / `ExchangePickerAdapter`）4 条长列表全部迁移为 `RecyclerView + ListAdapter + DiffUtil`；新增 `FocusTimerService.kt`（`FOREGROUND_SERVICE_SPECIAL_USE`，保留 `TIMER_ID = 8101` 与通知栏操作契约）与 `ReminderRescheduleWorker.kt`（`WorkManager 2.10.5` 每 6 小时自动兜底重排学习与待办闹钟），并补充 `Phase5ListModelsTest.kt`（详见 [REFACTORING-CHANGELOG.md](REFACTORING-CHANGELOG.md)）。

---

### Phase 6：正式发布工程化与未来扩展能力（按需推进）

1. **Release 打包与安全签名**：
   - 在 `app/build.gradle.kts` 的 `release` 构建开启 `isMinifyEnabled = true` 与 `isShrinkResources = true`，完善 `app/proguard-rules.pro`（保留 Room Entity/DAO、动态反射图标、JSON 序列化字段）。
   - 配置基于 `local.properties` / 环境变量的稳定签名 keystore（keystore 文件严禁入库），保证自己手机可长期覆盖升级。
   - **执行状态（6.1）**：已完成（2026-11-24），已启用 `isMinifyEnabled = true` + `isShrinkResources = true`，新增 `app/src/main/res/raw/keep.xml` 保护 `norm_type_*` 等动态分类图标，完善 `app/proguard-rules.pro` 与 `.gitignore`（屏蔽 `*.jks`/`*.keystore`/`keystore.properties`），配置 `local.properties`（`release.storeFile` 等）与环境变量（`PKM_STORE_*`）签名接入及 `CategoryIconKeepContractTest.kt`（详见 [REFACTORING-CHANGELOG.md](REFACTORING-CHANGELOG.md)）。第 6.2 节（WebDAV）与第 6.3 节（Compose 新页）作为后续按需扩展项保留。
2. **可选个人数据云同步（WebDAV）**：
   - 基于 Room + 现有 JSON/SQLite 快照机制，新增一个轻量 WebDAV 同步模块（兼容坚果云 / 自建 NAS），支持手动同步与 `WorkManager` 每日 Wi-Fi 自动备份，零服务器运维成本。
3. **可选新页采用 Jetpack Compose**：
   - 未来新增低耦合独立页面（如「年度学习与消费回顾」「WebDAV 同步设置」「桌面小组件 Glance」）时，可直接按需引入 Compose，无需改动已有稳定的核心页面。
4. **公网部署与服务端安全加固（Spring Boot 对外上线前必做）**：
   - **已完成（测试阶段云端基线，2026-11-27）**：
     - 阿里云 ECS（Ubuntu 24.04，2C2G + 2G SWAP）已通过 [server/Dockerfile](../server/Dockerfile) 与 [server/docker-compose.yml](../server/docker-compose.yml) 完成 `songguo-server:4.0.0` + `mysql:8.4` 容器化部署（`/opt/songguo`），并完成 18 张业务表初始化与 `/api/v1/health` 连通验证；
     - 生产随机密钥（`PKM_JWT_SECRET`、`PKM_ADMIN_TOKEN`、MySQL 强密码）已通过服务器 `/opt/songguo/.env`（`chmod 600`）注入，未写入仓库；MySQL `3306` 仅绑定 `127.0.0.1`，服务容器以非 root 运行并开启日志轮转；
     - 当前测试阶段安全组通过单 IP 白名单放行 `8080`，仅限本人测试使用。
   - **正式上线前必做安全变更清单**：
     1. **域名 + HTTPS（TLS）加密**：准备域名（大陆 ECS 需完成 ICP 备案），在 Nginx / Caddy（或 1Panel OpenResty）配置免费 Let's Encrypt 证书自动续签，并强制 HTTP → HTTPS 跳转，确保登录密码、Token 与业务同步报文全程加密；
     2. **收敛安全组与端口暴露面**：将 `server/docker-compose.yml` 的后端映射改为 `127.0.0.1:8080:8080`（禁止绕过 HTTPS 直连）；阿里云安全组**删除 `8080` 临时测试规则与无用的 `3389`（RDP）规则**，对外仅放行 `80` / `443`（按需收紧 `22`）；
     3. **防刷限流与按量带宽风控**：在 Nginx 层为 `/api/v1/auth/login`、`register` 等鉴权接口配置 `limit_req` 频率限制与请求体大小上限；在阿里云控制台调低按使用流量带宽峰值（如 `5~10 Mbps`）并开启费用/流量预警；
     4. **数据库定时自动备份**：在服务器配置每日凌晨 `cron` 任务自动 `mysqldump` 备份 `pkmapp` 数据库（滚动保留 7–30 天，建议同步至阿里云 OSS 异地存档）；
     5. **客户端强制 HTTPS**：Android 端生产同步地址统一改为 `https://`，并在 Release 构建中关闭明文 HTTP 允许（`usesCleartextTraffic=false`）。

---

## 4. 给执行 Agent 的直接启动指令（可直接复制使用）

你可以直接把下面这段提示词发给另一个 Agent，让它按计划分阶段推进：

```text
请先完整阅读本仓库根目录的 AGENTS.md、DESIGN.md、README.md 以及 docs/REFACTORING-PLAN.md。
请严格遵循 docs/REFACTORING-PLAN.md 中第 0 节的全部产品、视觉、自绘组件复用、测试契约与零数据丢失红线。

现在请从 Phase <填写编号，如 1> 开始执行，一次只完成一个 Phase：
1. 先列出本 Phase 涉及的全部文件改动清单与风险点；
2. 完成代码实现与对应单元测试；
3. 在 Windows PowerShell 中执行 `.\gradlew.bat testDebugUnitTest compileDebugAndroidTestSources assembleDebug --no-daemon --console=plain` 确保全绿；
4. 若涉及 UI 或数据迁移改动，请在模拟器上验证关键流程无回归；
5. 汇报改动清单、验证结果与下一步建议，等待我确认后再提交或进入下一 Phase。
```
