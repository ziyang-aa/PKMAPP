# 芽叶记（PKMAPP）重构执行变动记录（Changelog）

本文件记录各 Phase 在实际执行过程中，相对 [REFACTORING-PLAN.md](REFACTORING-PLAN.md) 原计划作出的调整、替代方案选择及理由，按 Phase 顺序追加。

---

## Phase 1：开启 Kotlin + Lifecycle + Coroutines 基础设施（2026-11-23）

### 方案与配置偏离

| 调整项 | 计划原文 | 实际做法 | 理由 |
| --- | --- | --- | --- |
| Kotlin Gradle 插件启用方式 | 在 `libs.versions.toml` 与 Gradle 构建中显式引入 `org.jetbrains.kotlin.android` 插件 | 不声明也不应用 `org.jetbrains.kotlin.android`，直接启用 **AGP 9.1.1 内置 Kotlin（Built-in Kotlin，KGP `2.2.10`）** | AGP 9.0+ 已默认内置 Kotlin 编译能力，再显式应用 `kotlin-android` 插件会触发冲突报错；沿用内置 Kotlin 是官方推荐演进路径，无需设置 `android.builtInKotlin=false` 回退开关 |
| Kotlin JVM 目标配置 DSL | 在 `app/build.gradle.kts` 中配置 `kotlinOptions { jvmTarget = "11" }` | 改用文件顶部 `import org.jetbrains.kotlin.gradle.dsl.JvmTarget` 与顶层块 `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }` | AGP 9 内置 Kotlin 已移除旧式 `kotlinOptions` DSL，强类型 `compilerOptions` 是标准写法，与 `compileOptions` 的 `JavaVersion.VERSION_11` 对齐 |
| `ksp` 插件版本占位 | 在 `libs.versions.toml` 中引入与 AGP `9.1.1` 匹配的 `ksp` 插件 | 在 `[versions]` / `[plugins]` 中声明 `ksp = "2.3.4"`（KSP2 与 Kotlin 解耦），**当前仅占位、不在任何 `build.gradle.kts` 中应用** | Phase 1 尚未使用 Room/注解处理，提前应用只会增加不必要的构建配置与下载负担；待 Phase 4 落地 Room 时再按当时最新稳定版复核启用 |
| 传递依赖对齐与版本选择 | 引入 `lifecycle-*-ktx`、`kotlinx-coroutines-*`、`fragment-ktx`、`activity-ktx` | 选定 `lifecycle = "2.9.4"`、`coroutines = "1.10.2"`、`activityKtx = "1.10.1"`、`fragment = "1.8.9"`；实际传递升级为：`lifecycle 2.6.2 → 2.9.4`、`activity 1.8.1 → 1.10.1`、`coroutines 1.6.4 → 1.10.2` | 经 `:app:dependencies` 验证均与 `minSdk 24` / `compileSdk 36.1` 及既有依赖树无冲突；`checkDebugDuplicateClasses` 与全量单测一次性通过 |
| Kotlin 源码目录约定 | 编写 1 个 Kotlin 单元测试验证与 Java 主代码互通 | 将 `AppDestinationKotlinInteropTest.kt` 放置在 `app/src/test/java/com/example/pkmapp/navigation/` | AGP 9 内置 Kotlin 默认同时编译 `src/*/java` 与 `src/*/kotlin` 下的 `.kt` 源码，放在对应包路径下与 Phase 2 计划的同包混合拆分约定一致 |

### 环境与构建网络备注

- 首次拉取新引入的 Maven Central 与 Google Maven 依赖（尤其是约 56.3 MB 的 `kotlin-compiler-embeddable-2.2.10.jar`）时，直连 Maven Central 与代理通道均出现偶发 TLS 握手中断或读取挂起。
- 实际生效的首次缓存拉取命令为全部流量走本机已启用的系统代理 `127.0.0.1:7897`，并限制并发与 HTTP 超时：
  ```powershell
  .\gradlew.bat testDebugUnitTest compileDebugAndroidTestSources assembleDebug --no-daemon --console=plain --max-workers=2 "-Dhttp.proxyHost=127.0.0.1" "-Dhttp.proxyPort=7897" "-Dhttps.proxyHost=127.0.0.1" "-Dhttps.proxyPort=7897" "-Dorg.gradle.internal.http.socketTimeout=30000" "-Dorg.gradle.internal.http.connectionTimeout=30000"
  ```
- **Windows PowerShell 注意事项**：通过 `gradlew.bat` 传参时，`-Dhttp.nonProxyHosts` 中不要包含 `|`（会被底层 `cmd.exe` 解析为管道符导致命令截断报错）。
- **仓库清洁**：未将任何本机代理参数写入仓库 `gradle.properties`。依赖写入 `D:\DevCache\.gradle` 缓存后，已复测不带任何代理参数的原始门禁命令（`.\gradlew.bat testDebugUnitTest compileDebugAndroidTestSources assembleDebug --no-daemon --console=plain`）直接全绿通过。

---

## Phase 2：拆解 4,088 行 `StudyFragment` 单体巨类（2026-11-24）

### 方案与结构偏离

| 调整项 | 计划原文 | 实际做法 | 理由 |
| --- | --- | --- | --- |
| 包路径与可见性边界 | 目标包结构草图将拆分后的学习子页置于 `feature/study/{focus,todo,stats,garden}` 子包下 | 将 `BaseStudyFragment.kt`、`StudySharedViewModel.kt` 与 4 个子页 Fragment 统一放在现有 `com.example.pkmapp.study` 包下 | 存量 `StudyPlan`、`StudySheet`、`StudyWork`、`StudyCatalog`、`StudyPlanDialogs`、`StudyWorkDialogs` 及 `StudyStore` / `StudyNotifications` 中大量方法均为 Java 包级私有（package-private）；保持在 `com.example.pkmapp.study` 同包内可 100% 原生互调，无需破坏封装或无意义地把 10 余个稳定 Java 类的内部成员暴露为 `public` |
| 大型弹窗与卡片视图构建器二次抽离 | 计划仅列出 `BaseStudyFragment.kt`、`StudySharedViewModel.kt` 与 4 个子页 Fragment | 在 4 个子页 Fragment（`StudyGardenFragment.kt` 205 行、`StudyTodoFragment.kt` 393 行、`StudyFocusFragment.kt` 457 行、`StudyStatsFragment.kt` 487 行，全部严格 `< 500` 行）旁，按既有 `StudyPlanDialogs` / `StudyWorkDialogs` 模式配套抽离高内聚 `internal object` 辅助类：`StudyFocusTimerCard.kt`、`StudyFocusDialogs.kt`、`StudyAgendaDialogs.kt`、`StudyTodoDialogs.kt`、`StudyTodoRows.kt`、`StudyStatsDialogs.kt` | 原 `StudyFragment.java` 达 4,088 行，其中底部抽屉弹窗与复杂计时卡片构建代码约占 2,200 行；若硬塞进 4 个 Fragment 会导致单文件突破 800~1,000 行。将弹窗与视图构建拆为职责单一的同包 `internal` 组件，使每个 Fragment 只专注页面编排与状态同步 |
| 跨页历史跳转与状态保留 | 用 `StudySharedViewModel` 接管 `HISTORY_REQUEST` 跨页筛选状态，不再靠 `FragmentResultListener` 硬传 | 在 `StudySharedViewModel` 中提供一次性消费的 `requestPlanHistory(planId)` / `consumePendingHistoryPlan()` 配合 `StatsScopeState` 与 `StudyStatsFragment.onSaveInstanceState`，同时用 `todoFilterState` 保持待办分栏与日期筛选跨子页切换不丢失 | 既彻底移除了 `FragmentResultListener("study-history")` 字符串 Bundle 硬传，又避免了“曾查看过已归档计划足迹后，后续从底部导航进入学习足迹仍残留旧计划筛选”的状态污染问题，且完整兼容 `ActivityScenario.recreate()` 重建恢复 |
| 动画与高频交互防闪刷 | 暴露 `StateFlow<StudyUiState>` 与 `StateFlow<TodoUiState>` | `StudySharedViewModel` 实时维护 `StateFlow<StudyUiState>` 与 `StateFlow<TodoUiState>` 快照供跨页观察及纯 JVM 单测校验，而子页 UI 刷新保持显式受控边界（专注暂停/继续调用 `syncRunningStateInPlace`，林地浇水等待 `StudyGardenView` 动画回调后再 `render()`） | 若对 `StudyStore.update` 做无差别自动全页 `render()`，会在林地浇水动画中途销毁 `StudyGardenView` 或在专注暂停时引发卡片闪刷；显式边界结合 `StateFlow` 快照兼顾了响应式状态共享与零闪烁体验 |
| 存量 Java 最小改动 | 更新 `MainActivity.java` 路由工厂并移除 `StudyFragment.java` | 除 `MainActivity.java`（更新路由及 `instanceof BaseStudyFragment` 三图层 Insets 豁免）外，仅修改 `StudyPlanDialogs.java` 两处 `StudyFragment.partnerDrawable` 调用为 `StudyUi.partnerDrawable`，彻底删除 4,088 行 `StudyFragment.java` | 将对既有稳定 Java 文件的触碰降到最低（仅 2 个文件极小改动） |
| 空计划状态下的防御性读取加固 | 拆分后等价迁移原页面逻辑 | 在 `StudyUiState.from()` 中直接从可空 `currentPlan` 读取目标时长默认值，并在 `StudyFocusFragment.updateTimer()` 入口增加 `currentPlan ?: return` 前置保护 | `StudyPlanFlowTest` 删除最后一个学习计划并触发 `ActivityScenario.recreate()` 与每秒 `tick` 时，原 `notebook.dailyGoal()` / `sessionGoal()` 的 `requireCurrent()` 断言会抛出异常；加固后空计划卡片与重建流程完全幂等安全 |

---

## Phase 3：拆解 `ProfileFragment` 与生活小屋统一 MVVM（2026-11-24）

### 方案与结构偏离

| 调整项 | 计划原文 | 实际做法 | 理由 |
| --- | --- | --- | --- |
| `LifeSharedViewModel` 包路径与可测数据门面 | 置于 `feature/life` 并直接聚合各 Store | 置于扁平包 `com.example.pkmapp.life`，提炼 `LifeDataSource` 接口与默认 `AndroidLifeDataSource` 实现，同时监听 `InMemoryLedgerRepository` 变更 | 与现有 `details/`、`charts/`、`record/`、`savings/`、`borrowing/`、`profile/` 扁平包层级一致；引入 `LifeDataSource` 既在 Phase 4 Room 落地前解耦了 `Context` / `SharedPreferences`，又使 `LifeSharedViewModel` 可在纯 JVM 环境下零 Mock 完整单测 |
| `ProfileFragment` 拆解粒度与线程模型 | 拆为 `ProfileFragment`（`< 350` 行）+ `AvatarCropSheetController` + `AssetAndLedgerDialogs` + `BackupCoordinator` | 移除 970 行 `ProfileFragment.java`，落地 138 行 `ProfileFragment.kt` + `AvatarCropSheetController.kt` + `BackupCoordinator.kt` + `AssetAndLedgerDialogs.kt` + `ProfileIdentityDialogs.kt`，并彻底淘汰原有的 2 个裸 `ExecutorService` 线程池，统一改用 `viewLifecycleOwner.lifecycleScope` + `Dispatchers.IO` | 将昵称编辑、签到日历与清空全部数据进一步内聚到 `ProfileIdentityDialogs.kt`，使主 Fragment 与所有子控制器均保持在 `138–290` 行的极薄规模，且异步 IO 随视图生命周期自动取消、无泄漏 |
| Java Fragment 订阅 `StateFlow` 桥接方式 | Java 侧调用处通过轻量桥接或 `LiveData` 兼容 | 在 `LifeSharedViewModel`、`ExchangeViewModel`、`StudySharedViewModel` 上提供基于 `owner.lifecycleScope` + `repeatOnLifecycle(Lifecycle.State.STARTED)` 的 `observe(LifecycleOwner, Consumer)` / `observeOverview` 桥接方法 | 无需额外引入 `LiveData` 中间层开销，Java Fragment 仅需 1 行即可安全绑定 `getViewLifecycleOwner()` 生命周期收集 `StateFlow`，同时保持存量稳定的 Java 视图构建逻辑不作无意义的语言重写 |
| `SavingsFragment` 差量重绘保护 | 观察 `LifeSharedViewModel` 自动同步 | 在 `SavingsFragment` 中缓存已渲染目标快照并按字段比对（`sameGoals`），仅在攒钱目标集合实际变化时重建卡片 | 既保证了备份导入或清空数据后攒钱页自动刷新，又避免无关字段（如昵称/签到）更新时误刷用户正在填写的新建空白目标卡 |
