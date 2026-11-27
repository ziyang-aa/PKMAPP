# 小松果（PKMAPP）项目交接备忘（HANDOFF）

## 1. 当前进度（`release/v5.0` 分支已收口并推送至 GitHub）
- **当前分支**：`release/v5.0`（基于 `release/v4.0` 演进；含品牌「小松果」、双空间、三段式登录、Room v3 多账号隔离与云同步、Spring Boot 3.5.11 + MySQL 8.4 后端，本机 `pkmapp` 库含真实用户数据，**严禁清库**）。
- **本版已完成 6 大迭代交付**：
  1. **月度预算 Room `v2 → v3` + MySQL 云同步 + 5 秒静默防抖同步**：
     - [LifeEntities.kt](../app/src/main/java/com/example/pkmapp/core/database/LifeEntities.kt) / [AppDatabase.kt](../app/src/main/java/com/example/pkmapp/core/database/AppDatabase.kt)（`MIGRATION_2_3` + `3.json`）、[LifeDaos.kt](../app/src/main/java/com/example/pkmapp/core/database/LifeDaos.kt)、[Ledger.java](../app/src/main/java/com/example/pkmapp/data/Ledger.java)、[MonthlyBudgetEvaluator.kt](../app/src/main/java/com/example/pkmapp/data/MonthlyBudgetEvaluator.kt)；
     - [LedgerCodec.java](../app/src/main/java/com/example/pkmapp/data/LedgerCodec.java) / [BackupCodec.java](../app/src/main/java/com/example/pkmapp/backup/BackupCodec.java) 向后兼容 `monthlyBudgetCents`；
     - [SyncRepository.kt](../app/src/main/java/com/example/pkmapp/core/sync/SyncRepository.kt) 补齐 `ledgers` 推送与拉取 LWW 回写；[SyncWorker.kt](../app/src/main/java/com/example/pkmapp/core/work/SyncWorker.kt) 新增 `scheduleDebouncedSync()`（已联网云账号 5 秒防抖静默同步，游客/离线自动跳过）并接入 8 处本地写入点；
     - 服务端 [schema.sql](../server/src/main/resources/sql/schema.sql)、[SchemaMigrationRunner.java](../server/src/main/java/com/example/pkmapp/server/common/SchemaMigrationRunner.java)（启动自动无损补列 `biz_ledgers.monthly_budget_cents`）、[SyncService.java](../server/src/main/java/com/example/pkmapp/server/sync/SyncService.java)。
  2. **生活小屋发丝预算条 + 暖瓷白去套框弹窗与键盘避让 + 攒钱卡片精简**：
     - 新增共享组件 [ForestSheetHelper.kt](../app/src/main/java/com/example/pkmapp/forest/ForestSheetHelper.kt)（暖瓷白抽屉 + Android 15/16 IME 键盘避让 + 报刊发丝线底线输入）；
     - [fragment_details.xml](../app/src/main/res/layout/fragment_details.xml) / [DetailsFragment.java](../app/src/main/java/com/example/pkmapp/details/DetailsFragment.java)：余额卡下方零套框报刊发丝线预算条（支持本月及历史月复盘，≥80% 暖琥珀、>100% 陶红），账本/新建/月份/预算统一改用暖瓷白抽屉，删除确认改用 `ForestDialogBuilder`；
     - [BorrowingFragment.java](../app/src/main/java/com/example/pkmapp/borrowing/BorrowingFragment.java) 接入 `ForestSheetHelper` 键盘避让；
     - [item_savings_goal.xml](../app/src/main/res/layout/item_savings_goal.xml) / [SavingsFragment.java](../app/src/main/java/com/example/pkmapp/savings/SavingsFragment.java)：卡片精简为插画 + 进度 + 底部一行「＋ 存入」与「编辑 ›」，分别弹出快捷存入抽屉（`+50/+100/+200`）与新建/编辑暖瓷白抽屉。
  3. **考期倒计时 + 锁屏通知实时进度条**：
     - 新增 [ExamCountdownFormatter.kt](../app/src/main/java/com/example/pkmapp/study/ExamCountdownFormatter.kt)，复用 `StudyPlan.due` 在 [StudyFocusFragment.kt](../app/src/main/java/com/example/pkmapp/study/StudyFocusFragment.kt) 与 [HomeFragment.java](../app/src/main/java/com/example/pkmapp/home/HomeFragment.java)「今日速览」右侧展示考期倒计时；
     - [StudyNotifications.java](../app/src/main/java/com/example/pkmapp/study/StudyNotifications.java) 补齐 `setProgress(1000, permille, false)` 与百分比详情，[FocusTimerService.kt](../app/src/main/java/com/example/pkmapp/study/FocusTimerService.kt) 刷新周期调整为 15 秒。
  4. **专注森林白噪音**：
     - 新增 [ForestSoundEngine.kt](../app/src/main/java/com/example/pkmapp/study/ForestSoundEngine.kt)（原生 `AudioTrack` 实时合成林间微雨 / 木屋炉火 / 林梢清风，零资源体积无缝循环，支持 5 秒试听与音量调节），绑定 [FocusTimerService.kt](../app/src/main/java/com/example/pkmapp/study/FocusTimerService.kt) 随专注计时自动启停，[StudyFocusTimerCard.kt](../app/src/main/java/com/example/pkmapp/study/StudyFocusTimerCard.kt) / [StudyFocusFragment.kt](../app/src/main/java/com/example/pkmapp/study/StudyFocusFragment.kt) 接入白噪音抽屉。
  5. **暖纸风双空间桌面小组件（免开屏直达）**：
     - 新增 [FocusWidgetProvider.kt](../app/src/main/java/com/example/pkmapp/widget/FocusWidgetProvider.kt)、[LifeBudgetWidgetProvider.kt](../app/src/main/java/com/example/pkmapp/widget/LifeBudgetWidgetProvider.kt)、[ForestWidgets.kt](../app/src/main/java/com/example/pkmapp/widget/ForestWidgets.kt) 及 `4×2` 可缩放暖纸布局；
     - [SplashActivity.java](../app/src/main/java/com/example/pkmapp/SplashActivity.java) → [MainActivity.java](../app/src/main/java/com/example/pkmapp/MainActivity.java) 支持 `EXTRA_WIDGET_DESTINATION` 免开屏直达专注页 / 记一笔 / 明细页。
  6. **图表页顶栏与水彩伙伴极简重塑（3+B 方案）**：
     - [fragment_charts.xml](../app/src/main/res/layout/fragment_charts.xml) / [ChartsFragment.java](../app/src/main/java/com/example/pkmapp/charts/ChartsFragment.java)：`支出 / 收入` 放大为 `22sp / 19sp` 衬线发丝线切换并同行右置紧凑 `周 / 月 / 年`；去掉暖纸区白胶囊，时间翻页条（`‹ 2026年11月 ▾ ›`）无框收纳入瓷白趋势卡顶栏并支持周/月/年三模式一键翻页与暖瓷白抽屉选月；`104×92dp` 水彩小熊/小松鼠探头栖于趋势卡右上沿，支持点击直接切换支出/收入。

## 2. 已通过验证
- `mvn -f d:\pkmAPP\server\pom.xml test`：`PkmServerIntegrationTest` 实连本地 MySQL 8.4 全链路通过（1/1，已自动无损补列 `biz_ledgers.monthly_budget_cents` 并验证推拉同步）。
- `.\gradlew.bat testDebugUnitTest compileDebugAndroidTestSources assembleDebug --console=plain`：`BUILD SUCCESSFUL`（134 项单测全绿）。
- **Android 16 模拟器实测验收（`Pixel_10_Pro_XL`, API 36）**：
  - `connectedDebugAndroidTest`（`AppDatabaseMigrationAndIsolationTest`）：Room `v1 → v2 → v3` 无损迁移与多账号隔离 **2/2 PASS**；
  - 5 大模块 + 图表页 `3+B` 改版（支出月、点小熊切收入月、周视图防撞车、一键翻月与暖瓷白年月抽屉）逐页操作与截屏验收 **ALL PASS**（0 缺陷，Logcat 0 崩溃）。

## 3. 下一步待办
1. 如需同步安装到 vivo 真机或重启服务端 JAR：连接真机后 `adb install -r app/build/outputs/apk/debug/app-debug.apk`；服务端先停旧进程再 `mvn -f d:\pkmAPP\server\pom.xml package -DskipTests` 与 `java -jar`。
2. 后续按需规划 `v5.x` 迭代或合并 `release/v5.0` 至 `main`。
