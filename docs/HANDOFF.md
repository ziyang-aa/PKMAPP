# 小松果（PKMAPP）项目交接备忘（HANDOFF）

## 1. 当前版本基线（`v4.0` 已发布分支 `codex/v4-release`）
- **Android 端（`d:\pkmAPP`）**：已安装最新 `v4.0` Debug APK 至用户 vivo 真机（`10AE632GDX000H9`，Android 16 / API 36）并推送至 `origin/codex/v4-release`：
  1. **品牌与方案 A 双空间**：更名「小松果」；[HomeFragment.java](../app/src/main/java/com/example/pkmapp/home/HomeFragment.java) 左置头像胶囊 + 右置同步状态徽章；[HomeDrawerController.kt](../app/src/main/java/com/example/pkmapp/account/HomeDrawerController.kt) 暖瓷白侧边栏；生活小屋 4 格底栏 + 右下松绿悬浮 `＋ 记一笔`；新增 [VaultFragment.kt](../app/src/main/java/com/example/pkmapp/vault/VaultFragment.kt) 与 [FeedbackFragment.kt](../app/src/main/java/com/example/pkmapp/feedback/FeedbackFragment.kt)。
  2. **三段式全中文登录门禁与可动小松鼠**：[AuthActivity.kt](../app/src/main/java/com/example/pkmapp/account/AuthActivity.kt)（选号 / 登录注册 / Material3 浮动标签 + 游客体验 + 柔和按压态）；[SquirrelMascotView.kt](../app/src/main/java/com/example/pkmapp/forest/SquirrelMascotView.kt)、[SquirrelSpeechBubble.kt](../app/src/main/java/com/example/pkmapp/forest/SquirrelSpeechBubble.kt)、[ForestLoadingView.kt](../app/src/main/java/com/example/pkmapp/forest/ForestLoadingView.kt)。
  3. **Room `v1 → v2` 多账号隔离与真机云同步**：12 张表升级为 `ownerId` 复合主键 + 同步元字段；修复离线账号升云（[AuthRepository.kt](../app/src/main/java/com/example/pkmapp/core/account/AuthRepository.kt) / [AccountSheets.kt](../app/src/main/java/com/example/pkmapp/account/AccountSheets.kt)）、Android 15/16 底部弹窗键盘避让（[StudySheet.java](../app/src/main/java/com/example/pkmapp/study/StudySheet.java)）及 URL 全角字符/重复 `http://` 规范化（[CryptoSessionStore.kt](../app/src/main/java/com/example/pkmapp/core/security/CryptoSessionStore.kt)）。
  4. **学习小屋专注卡、科目库、锁屏通知与全局去套框弹窗**：
     - [StudyFocusTimerCard.kt](../app/src/main/java/com/example/pkmapp/study/StudyFocusTimerCard.kt) / [StudyFocusFragment.kt](../app/src/main/java/com/example/pkmapp/study/StudyFocusFragment.kt)：报刊发丝线时长选择条、松绿釉面胶囊 CTA、`updateSessionGoalInPlace()` 零闪屏；
     - [StudyPlanDialogs.java](../app/src/main/java/com/example/pkmapp/study/StudyPlanDialogs.java)：科目库报刊发丝线双标签 + `popTo(formPane)` 直达书架；
     - [StudyNotifications.java](../app/src/main/java/com/example/pkmapp/study/StudyNotifications.java) / [FocusTimerService.kt](../app/src/main/java/com/example/pkmapp/study/FocusTimerService.kt) / [BaseStudyFragment.kt](../app/src/main/java/com/example/pkmapp/study/BaseStudyFragment.kt)：开始前通知权限引导 + 静默 `IMPORTANCE_DEFAULT` + `VISIBILITY_PUBLIC` 锁屏实时时长显示（渠道 `study_timer_live`）；
     - [ForestDialogBuilder.java](../app/src/main/java/com/example/pkmapp/forest/ForestDialogBuilder.java)：参考 MD3 / shadcn/ui 彻底去除多层方框套方框，长按钮自动堆叠不截断。
- **Spring Boot 3.5.11 + MySQL 8.4 后端（`d:\pkmAPP\server`）**：
  - 本机 `MySQL84`（`127.0.0.1:3306`, `root`/`123456`）已建 `pkmapp` 库 17 张表，存有用户真机同步的真实账号、账本与 170 条流水（**严禁清库**）；
  - 联调命令：`java -jar d:\pkmAPP\server\target\songguo-server-4.0.0.jar` + `adb -s 10AE632GDX000H9 reverse tcp:8080 tcp:8080`。

## 2. 已通过验证
- `mvn -f d:\pkmAPP\server\pom.xml test`：`PkmServerIntegrationTest` 实连本地 MySQL 8.4 全链路通过（1/1）。
- `.\gradlew.bat testDebugUnitTest compileDebugAndroidTestSources assembleDebug --console=plain`：`BUILD SUCCESSFUL`。

## 3. 新会话直接承接的下一步（已批准的短期迭代计划）
用户已通过 `/plan` + `/grill-me` 批准 **5 模块短期迭代计划**（定稿文档见上轮对话 artifact `short_term_sprint_plan.md`，核心决策如下，新会话可直接按顺序执行，无需重复提问）：
1. **月度预算 Room `v2 → v3` + MySQL 云同步 + 5 秒静默防抖同步**：
   - `LedgerEntity` 与 `biz_ledgers` 无损新增 `monthlyBudgetCents` / `monthly_budget_cents`（`ALTER TABLE` 保留真机现有数据）；
   - 按账本设固定月基准预算，支持本月及历史月复盘，≥80% 暖琥珀提示，>100% 陶红超支提示；
   - `SyncRepository` 补齐 `ledgers` 拉取 LWW 回写；`BackupCodec` 向后兼容可选字段；`SyncWorker.scheduleDebouncedSync()` 在已联网云登录态下防抖 5 秒自动静默同步（游客/离线跳过）。
2. **生活小屋发丝预算条 + 去套框收口**：
   - [DetailsFragment.java](../app/src/main/java/com/example/pkmapp/details/DetailsFragment.java)：余额卡下方加零套框报刊发丝线预算条；旧版账本/新建/月份/删除弹窗统一改用暖瓷白抽屉或 `ForestDialogBuilder` + 键盘避让；[BorrowingFragment.java](../app/src/main/java/com/example/pkmapp/borrowing/BorrowingFragment.java) 补键盘避让；
   - [SavingsFragment.java](../app/src/main/java/com/example/pkmapp/savings/SavingsFragment.java) / [item_savings_goal.xml](../app/src/main/res/layout/item_savings_goal.xml)：卡片精简为插画+进度+底部一行「＋ 存入」与「编辑」，「存入」弹快捷金额窗（`+50/+100/+200`），「编辑」弹暖瓷白抽屉。
3. **考期倒计时 + 锁屏通知进度条**：
   - 复用 `StudyPlan.due`，在专注页与首页「今日速览」右侧呈现考期倒计时；`StudyNotifications.buildTimerNotification` 补上 `setProgress` 实时进度条。
4. **专注森林白噪音**：
   - 新增 `ForestSoundEngine.kt`（原生 `AudioTrack` 实时合成：林间微雨 / 木屋炉火 / 林梢清风，零体积无缝循环），绑定 `FocusTimerService` 随专注计时自动启停，选单支持 5 秒试听与音量调节。
5. **暖纸风双空间桌面小组件（免开屏直达）**：
   - 新增 `FocusWidgetProvider` 与 `LifeBudgetWidgetProvider`（默认 `4×2` 可缩放），通过 `SplashActivity` → `MainActivity` 跳过开屏直达专注页 / 记一笔 / 明细页。
