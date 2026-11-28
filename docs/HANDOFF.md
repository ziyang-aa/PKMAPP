# 小松果（PKMAPP）项目交接备忘（HANDOFF）

## 1. 当前进度（Stage 1 & Stage 2 收口完成 · Stage 3 设计稿待定稿）
- **当前分支**：`v5.1`（工作区尚未 `git commit`/`push`，待用户确认后提交归档）。
- **已完成的 Stage 1（P0 端云稳定性地基）**：
  1. **Android 401 AccessToken 自动无感续期**：[SyncApiClient.kt](../app/src/main/java/com/example/pkmapp/core/sync/SyncApiClient.kt) 新增 `refresh()` 对接 `/api/v1/auth/refresh` 并透出 `httpStatus/unauthorized`；[SyncRepository.kt](../app/src/main/java/com/example/pkmapp/core/sync/SyncRepository.kt) 在 Push / Feedback / Pull 任一环节遇 401 时自动轮换保存新 `(accessToken, refreshToken)` 并单次重试，RefreshToken 过期时零丢数保护并提示重新登录；[SyncRepositoryReliabilityTest.kt](../app/src/androidTest/java/com/example/pkmapp/core/sync/SyncRepositoryReliabilityTest.kt) 补齐 2 条 401 续期与过期保护测试。
  2. **阿里云 ECS 每日 MySQL 自动备份**：已在 ECS 部署 `/opt/songguo/scripts/backup_mysql.sh`（`mysqldump --single-transaction --no-tablespaces --routines --triggers --default-character-set=utf8mb4 | gzip`，`600` 权限，14 天自动清理）并写入 `30 3 * * *` `crontab`，演练生成 18 表完整 `utf8mb4` `.sql.gz` 备份通过校验。
- **已完成的 Stage 2（`v5.1.0` 收口准备）**：
  1. [app/build.gradle.kts](../app/build.gradle.kts) 升级 `versionCode = 6`、`versionName = "5.1.0"`；
  2. [HomeDrawerController.kt](../app/src/main/java/com/example/pkmapp/account/HomeDrawerController.kt) 与 [FeedbackRepository.kt](../app/src/main/java/com/example/pkmapp/core/feedback/FeedbackRepository.kt) 改为读取 `BuildConfig.VERSION_NAME`；
  3. 同步更新 [README.md](../README.md) 与本交接文档。
- **Stage 3（`v5.2` 学习小屋「自律打卡」）设计稿已就绪**：
  - 已生成可交互 HTML 原型设计稿（含 5 Tab 底栏方案 A、`ForestCalendarView` 月历补卡/感悟、单次与定量进度环打卡、小林地水滴联动与零套框新建表单），待用户拍板后正式进入 Room `v4→v5` + 端云同步实现。

## 2. 已通过验证
- `connectedDebugAndroidTest`（`SyncRepositoryReliabilityTest` on `emulator-5554` / Android 16）：**7/7 PASS**（含 401 自动续期轮换与 RefreshToken 过期本地零丢数保护）。
- Android 全量门禁 `testDebugUnitTest compileDebugAndroidTestSources assembleDebug` + Spring Boot `mvn package`（详见本轮收口验证结果）。
- 阿里云 ECS（`8.136.157.206`）：`songguo-server:4.0.0` + `mysql:8.4` 均 `healthy`（18 张表，干净库仅留 `emu_cloud_test@songguo.app` 供查验），1Panel v2.5.1 运行中，`03:30` 自动备份已生效。

## 3. 下一步待办
1. 待用户确认后，执行 `v5.1` 的干净 `git commit` 与推送至 GitHub。
2. 待用户审阅「自律打卡」可交互 HTML 设计稿并拍板细节后，执行 **Stage 3**（Room `v4→v5`、学习小屋第 5 Tab `StudyHabitFragment`、小林地水滴联动、Spring Boot `biz_study_habits`/`biz_study_habit_logs` 云同步与 ECS 更新、子代理实机截屏验收）。
3. 待用户购入域名后执行 **Stage 4**（1Panel OpenResty HTTPS 自动续签、安全组收口与 Release 签名包）。

