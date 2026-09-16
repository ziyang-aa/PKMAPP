# 账单导入导出实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with review checkpoints.

**Goal:** 在“我的”页面提供账单备份导出和覆盖/合并导入，并恢复账本明细、图表、攒钱、资产和借钱数据。

**Architecture:** 增加独立的备份数据模型和 JSON 编解码器，把现有账本、攒钱、资产和借钱存储统一封装成一个备份快照。导入先完整校验，再由数据存储一次性替换或按规则合并；页面使用 Android Storage Access Framework 选择文件，并在完成后重新渲染相关数据。

**Tech Stack:** Java、AndroidX Activity Result API、Android `JSONObject`、SharedPreferences、JUnit、Espresso。

---

## 实施任务

### 1. 备份数据模型和 JSON 编解码

文件范围：新增 `backup` 包中的备份快照、交易去重和 JSON 编解码类；必要时为攒钱、资产和借钱存储补充快照/导入接口。

顺序：先为完整导出、完整解析、非法数据拒绝和交易重复键编写单元测试，再实现最小数据层。备份格式使用设计文档中的版本字段和数据区段，保持金额使用分，日期使用毫秒。

验证：运行备份相关单元测试和全部 `testDebugUnitTest`。

### 2. 存储层的覆盖和合并导入

文件范围：`InMemoryLedgerRepository`、`SavingsGoalStore`、`AssetStore`、`BorrowingRecordStore` 及其测试；如需统一入口，增加一个小型导入协调器。

顺序：增加覆盖导入、合并导入和导入结果统计。合并交易只按发生时间、金额、备注去重；目标、资产和借钱记录按稳定编号处理。所有数据校验完成后才修改本地状态，并在成功后发出账本数据变更通知。

验证：覆盖空数据、重复数据、跨账本数据、重复攒钱目标和损坏备份文件；运行相关单元测试。

### 3. “我的”页面导出/导入操作

文件范围：`ProfileFragment.java`、`fragment_profile.xml`、相关字符串和必要的测试资源。

顺序：增加导出和导入入口；导出使用 `CreateDocument`，导入使用 `OpenDocument`；选中文件并通过校验后显示覆盖/合并选择；操作结果和新增/跳过数量在页面提示。

验证：运行 Android 测试源码编译和页面入口测试，确认文件选择回调失败时不修改数据。

### 4. 页面刷新和集成验证

文件范围：`DetailsFragment`、`ChartsFragment`、`SavingsFragment`、必要的 `MainActivity`/数据监听代码及测试。

顺序：让导入后的明细、图表和攒钱页面重新读取数据；确保当前“我的”页面资产摘要也更新。补充导入后监听通知和页面重建场景测试。

验证：运行 `testDebugUnitTest compileDebugAndroidTestSources assembleDebug`；如果有可用设备，再运行 `connectedDebugAndroidTest`。

### 5. 文档和最终检查

更新 README 中的备份使用方式和格式说明，检查 `.gitignore` 不会忽略用户需要的备份代码或资源。执行 `git diff --check`，确认没有把构建产物、密钥或本机配置加入提交。

完成标准：可导出一个完整 `.pkmbackup.json`；重装后的空 App 可覆盖导入并恢复数据；非空 App 可合并导入并按三字段跳过重复消费；导入失败不破坏现有数据；构建和相关测试通过。
