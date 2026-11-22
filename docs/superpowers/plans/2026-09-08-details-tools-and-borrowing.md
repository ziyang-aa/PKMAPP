# 明细选择器与森林小工具实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. 本项目当前按用户要求在同一工作区内执行，且在用户明确要求前不提交、不推送。

**Goal:** 将 HTML 已确认的明细视觉层级同步到 Android，并完成联网汇率、借出/借入统计和记账联动。

**Architecture:** 使用 Java/XML Fragment 保持现有应用结构。选择器使用自定义 PopupWindow/BottomSheetDialog，网络由独立 `ExchangeRateService` 在线程池中请求，借钱记录由独立 `BorrowingRecordStore` 使用 SharedPreferences JSON 持久化；工具页通过 `AppDestination` 保持独立路由但不加入底部导航。

**Tech Stack:** Java 11 source compatibility, Android XML/ViewBinding, AndroidX Fragment/RecyclerView/Material, `HttpURLConnection`, `org.json`, JUnit JVM tests。

**Spec:** `docs/superpowers/specs/2026-09-08-details-tools-and-borrowing-design.md`

## Global Constraints

- 直接修改 `D:\pkmAPP`，不切回 WSL；不删除、覆盖或批量暂存既有未追踪文件。
- 保持 Java + XML，不使用 Kotlin、Compose 或新的网络依赖。
- 使用暖纸张森林绘本风，避免深色玻璃拟态和系统老式选择器。
- 用户明确要求后才提交或推送；当前阶段只保留本地修改。
- 实现前按 TDD 先写失败测试；完成前执行真实测试、构建和模拟器检查。

### Task 1: 借钱领域模型与汇率纯逻辑

**Files:**

- Create: `app/src/main/java/com/example/pkmapp/borrowing/BorrowingDirection.java`
- Create: `app/src/main/java/com/example/pkmapp/borrowing/BorrowingRecord.java`
- Create: `app/src/main/java/com/example/pkmapp/borrowing/BorrowingSummary.java`
- Create: `app/src/main/java/com/example/pkmapp/exchange/ExchangeRateSnapshot.java`
- Create: `app/src/main/java/com/example/pkmapp/exchange/ExchangeRateCalculator.java`
- Test: `app/src/test/java/com/example/pkmapp/borrowing/BorrowingSummaryTest.java`
- Test: `app/src/test/java/com/example/pkmapp/exchange/ExchangeRateCalculatorTest.java`

- [ ] 写失败测试：验证金额/人物/日期必填，按借出/借入方向汇总，以及 `amount × rate` 保留两位小数。
- [ ] 运行对应测试并确认因类/逻辑缺失失败。
- [ ] 实现最小不可变模型与纯计算逻辑。
- [ ] 重新运行对应测试并确认通过。

### Task 2: 借钱本地存储与日期分组

**Files:**

- Create: `app/src/main/java/com/example/pkmapp/borrowing/BorrowingRecordStore.java`
- Create: `app/src/main/java/com/example/pkmapp/borrowing/BorrowingDateGroup.java`
- Test: `app/src/test/java/com/example/pkmapp/borrowing/BorrowingRecordStoreTest.java`
- Test: `app/src/test/java/com/example/pkmapp/borrowing/BorrowingDateGroupTest.java`

- [ ] 写失败测试：验证空人物/空日期拒绝保存、保存后按账本读取、JSON 往返不丢字段、列表按日期倒序分组。
- [ ] 运行测试确认失败原因是存储和分组功能缺失。
- [ ] 使用 SharedPreferences JSON 实现 `BorrowingRecordStore`，坏条目跳过而不是崩溃；实现日期分组纯逻辑。
- [ ] 运行测试确认通过，并保持现有交易仓储不被改写。

### Task 3: 明细月份与账本纸张选择器

**Files:**

- Modify: `app/src/main/java/com/example/pkmapp/details/DetailsFragment.java`
- Modify: `app/src/main/res/layout/fragment_details.xml`
- Create: `app/src/main/res/layout/dialog_details_ledger_picker.xml`
- Create: `app/src/main/res/layout/dialog_details_month_picker.xml`
- Create: `app/src/main/res/layout/item_details_picker_option.xml`
- Create/modify: `app/src/main/res/drawable/bg_details_picker_sheet.xml`, `bg_details_picker_option.xml`, `bg_details_picker_selected.xml`
- Test: `app/src/test/java/com/example/pkmapp/details/DetailsPickerLabelTest.java`

- [ ] 写失败测试：验证月份显示不带旧式滚轮文案，并验证当前月恢复逻辑的年月值。
- [ ] 运行测试确认失败。
- [ ] 用纸张底部弹窗替换 `NumberPicker` 和普通账本单选对话框；保持现有月份过滤、账本切换和新建账本行为。
- [ ] 运行测试并在小屏模拟器点击月份、账本和取消/确认路径。

### Task 4: 联网汇率服务与独立页面

**Files:**

- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/example/pkmapp/exchange/ExchangeRateService.java`
- Create: `app/src/main/java/com/example/pkmapp/exchange/ExchangeFragment.java`
- Create: `app/src/main/res/layout/fragment_exchange.xml`
- Create/modify: `app/src/main/res/drawable/bg_exchange_*`, `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/com/example/pkmapp/navigation/AppDestination.java`
- Modify: `app/src/main/java/com/example/pkmapp/MainActivity.java`
- Test: `app/src/test/java/com/example/pkmapp/exchange/ExchangeRateJsonParserTest.java`

- [ ] 写失败测试：验证合法 JSON 的 base/quote/rate/date 解析和非法响应拒绝。
- [ ] 运行测试确认失败。
- [ ] 增加 INTERNET 权限，使用 `HttpURLConnection` 请求 `/v2/rate/{base}/{quote}`，10 秒连接/读取超时，主线程只更新 UI；缓存最近成功快照。
- [ ] 实现 CNY/USD/EUR/JPY/HKD 选择、金额输入、交换、结果和加载/失败/缓存状态。
- [ ] 运行测试并在模拟器联网与断网状态各检查一次。

### Task 5: 借钱统计页面与记账联动

**Files:**

- Create: `app/src/main/java/com/example/pkmapp/borrowing/BorrowingFragment.java`
- Create: `app/src/main/res/layout/fragment_borrowing.xml`
- Create/modify: `app/src/main/res/drawable/bg_borrowing_*`, `app/src/main/res/layout/item_borrowing_record.xml`
- Modify: `app/src/main/java/com/example/pkmapp/navigation/AppDestination.java`
- Modify: `app/src/main/java/com/example/pkmapp/MainActivity.java`
- Modify: `app/src/main/java/com/example/pkmapp/record/RecordFragment.java`
- Modify: `app/src/main/res/layout/fragment_record.xml`
- Test: `app/src/test/java/com/example/pkmapp/record/RecordBorrowingLinkTest.java`

- [ ] 写失败测试：借出/借入分类映射到方向，保存后能生成带 sourceTransactionId 的预填请求，人物为空不能完成工具记录。
- [ ] 运行测试确认失败。
- [ ] 收入侧增加“借入”分类；保存借出/借入交易后弹出是否统计；“去填写”导航到借钱页并带入金额/日期/方向。
- [ ] 借钱页实现两个方向胶囊、必填校验、备注、按日期列表、总额/笔数和本地保存。
- [ ] 运行测试并在模拟器完成借出、借入、跳过统计和重启后数据保留检查。

### Task 6: 集成验证与交接记录

**Files:**

- Modify: `docs/HANDOFF.md`
- Modify: `docs/HANDOFF.next-session.md`
- Generated only if absent: `build-details-tools-*.png`

- [ ] 运行完整 `testDebugUnitTest`、`assembleDebug` 和 HTML 四项检查。
- [ ] 安装 APK 到当前可用模拟器，检查明细选择器、汇率页、借钱页、记账联动和无崩溃日志。
- [ ] 检查 375dp 小屏、横屏和大字号下的基本可用性；记录当前环境无法覆盖的项目。
- [ ] 更新交接文档，说明实际命令、结果、未解决限制和素材使用情况。
- [ ] 用户明确要求后再精确暂存相关文件、提交和推送；本阶段不自动执行。
