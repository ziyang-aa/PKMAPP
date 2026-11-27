# 芽叶记（PKMAPP）

> **当前版本：`v3.0.0`** · 个人「学习专注 + 生活记账」双空间 Android 应用（全量本地离线存储）

**芽叶记**是一款以暖纸手账与手绘森林绘本为视觉基调的个人学习与生活记账双空间 Android 应用，完整保留 14 个自绘 `Canvas` 组件，采用 **Java 11 + Kotlin 2.2 混编**、双空间 MVVM（`StateFlow`）以及 **Jetpack Room + Preferences DataStore** 本地持久化架构。

---

## 核心功能

### 1. 森林门厅（双空间入口）
- **双小屋分流**：手绘蜡笔全景门厅连接「学习小屋（松栗）」与「生活小屋（小刺猬与小芽）」，支持记住上次进入的小屋。
- **今日速览**：汇总今日专注时长、待办事项数与本月支出，点击直达对应页面；存在进行中或暂停中的专注会话时，顶部显示快捷续接入口。

### 2. 学习小屋（Study Space）
- **专注（森林自习室）**：支持多学习计划（考研 / 期末 / 自定义科目书架）、正计时 / 倒计时切换、自定义专注时长、屏幕常亮与前台服务通知栏控时，结束时可修正时长/科目并记录学习手记与下次续接点。
- **待办（独立草稿本）**：与学习计划解耦，采用互斥三分区 **「今天（逾期置顶 + 今日）/ 计划（未来按日 + 随时可做）/ 已完成（按实际完成日期与时刻倒序日志）」**，内置自绘瓷白日历按日期筛选（带待办/逾期/已完成三色圆点标记与相邻日翻页）、底部快记栏以及独立截止提醒。
- **学习足迹（统计）**：累计专注摘要（次数/天数/日均/连续打卡）、科目占比环形图（点按高亮）、科目时长条形图、每日趋势、6 周森林专注热力图以及历史手记搜索/补记/待回顾。
- **我的小林地（成长花园）**：每累计专注 25 分钟获得 1 水滴，每日首次达标获得 1 种子（也可用 6 水滴兑换 1 种子）；浇灌养成手绘蜡笔成树并永久收录进林地收藏。

### 3. 生活小屋（Life Space）
- **明细**：多账本切换、本月收入/支出/结余统计、按日期分组流水，内嵌 **24 种 ECB 货币汇率换算**（支持本地缓存离线换算）与 **借钱统计**。
- **图表**：周 / 月 / 年收支趋势折线图与分类占比排行。
- **记一笔**：23+1 项支出与 10+1 项收入分类（支持自定义）、四则计算器、日期选择与借贷自动联动。
- **攒钱**：创建多个攒钱目标，记录存入进度与幼芽成长阶段。
- **我的（森林档案）**：头像裁剪、昵称编辑、每日签到、总资产快照与账单 JSON 导出/导入。

---

## 技术架构

| 层级 | 技术选型与实现要点 |
| --- | --- |
| **语言与构建** | Java 11 + Kotlin 2.2.10 混编、AGP 9.1.1、KSP、ViewBinding、Release R8 代码与资源压缩 |
| **界面与自绘组件** | 原生 View 体系 + 14 个自绘 `Canvas` 视图（日历、时钟进度条、统计图表、热力图、成长花园、草稿纸等）+ 悬浮毛玻璃底栏 |
| **架构与状态管理** | 双空间 MVVM（`ViewModel` + `StateFlow` + `Coroutines`）、`RecyclerView ListAdapter + DiffUtil` |
| **本地存储** | Jetpack Room（SQLite `pkm_app.db`，12 张规范化表 + 自动化无损迁移）+ Preferences DataStore |
| **后台任务** | `Foreground Service`（专注锁屏/后台计时保活）、`WorkManager`（每 6 小时自动兜底重排学习与待办截止提醒） |

---

## 项目结构

```text
PKMAPP/
├─ README.md                                  # 项目文档
├─ DESIGN.md                                  # 视觉与交互设计规范
├─ build.gradle.kts / settings.gradle.kts     # 根工程 Gradle 配置
└─ app/                                       # Android 应用模块
   ├─ build.gradle.kts                        # 模块配置（minSdk 24, targetSdk 36, version 3.0.0）
   ├─ proguard-rules.pro                      # R8 混淆与动态资源保护规则
   ├─ schemas/                                # Room 导出的数据库版本 Schema JSON
   └─ src/
      ├─ main/java/com/example/pkmapp/
      │  ├─ SplashActivity.java / MainActivity.java # 启动页、单 Activity 容器与双空间导航
      │  ├─ navigation/                       # 全局页面枚举（AppDestination）
      │  ├─ home/                             # 森林门厅（HomeFragment）
      │  ├─ forest/                           # 公共视觉组件库与自绘控件（日历、分段栏、草稿纸、毛玻璃底栏等）
      │  ├─ study/ & todo/                    # 学习小屋四页 MVVM、自绘图表/热力图/花园、前台服务与独立待办
      │  ├─ core/                             # Room 数据库（12 张表）、无损迁移器与 Preferences DataStore
      │  ├─ life/ & data/                     # 生活小屋共享 MVVM 状态层与 Room 仓库
      │  └─ details/ charts/ record/ savings/ borrowing/ exchange/ profile/ backup/
      ├─ test/                                # 38 个 JVM 本地单元测试文件（123 项用例）
      └─ androidTest/                         # 7 个真机/模拟器 UI 与集成测试
```

---

## 数据存储与备份

所有数据均保存在设备本地，无需云端账号即可完整使用：

- **结构化存储与无损迁移**：账本、交易、借还记录、攒钱目标、自定义分类、学习计划/科目/记录/事项/回顾/林地与独立待办统一持久化于 **Jetpack Room（SQLite `pkm_app.db`）**，个人档案与偏好存储于 **Preferences DataStore**；从旧版升级时自动先备份原始 JSON（`filesDir/migration_backup_v1/`）再单事务无损迁入。
- **生活记账备份**：可在 **「生活小屋 → 我的 → 导出/导入账单」** 保存或恢复向后兼容的 `.pkmbackup.json`（支持覆盖或按「时间 + 金额 + 备注」去重合并）。
- **学习备份**：可在 **「学习小屋 → 当前计划 → 学习备份」** 导出或合并恢复 JSON 备份。

---

## 快速开始与构建测试

在项目根目录（Windows PowerShell）执行编译与本地单元测试：

```powershell
# 运行全部本地单元测试、编译测试代码并打包 Debug APK
.\gradlew.bat testDebugUnitTest compileDebugAndroidTestSources assembleDebug --console=plain

# 连接模拟器或真机运行 UI 集成测试
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

Debug 安装包输出路径：`app/build/outputs/apk/debug/app-debug.apk`。

### Release 混淆打包与签名

Release 构建已启用 R8 代码混淆与资源压缩（`isMinifyEnabled = true`、`isShrinkResources = true`）。在项目根目录 `local.properties`（或环境变量 `PKM_STORE_FILE`、`PKM_STORE_PASSWORD`、`PKM_KEY_ALIAS`、`PKM_KEY_PASSWORD`）配置签名信息后执行：

```powershell
.\gradlew.bat assembleRelease
```

---

## 版本演进

### `v3.0.0`（对比 `v2.0.0`）
- **新增**：
  - **Kotlin 混编与现代存储底座**：接入 Kotlin 2.2、KSP、Coroutines 与 `StateFlow`；新增 `AppDatabase`（Room SQLite 12 张规范化表）与 `AppPreferences`（Preferences DataStore），内置旧版 `SharedPreferences` 单事务无损迁移器及迁移前冷备机制。
  - **专注前台服务与提醒兜底**：新增 `FocusTimerService` 保障锁屏与切后台精准计时，接入 `WorkManager`（`ReminderRescheduleWorker`）周期重排学习与待办截止提醒。
  - **Release 混淆压缩工程化**：开启 R8 代码混淆与资源压缩（安装包体积由 `28.3 MB` 缩减至 `19.9 MB`，降幅约 30%），配置动态分类图标保留规则与正式签名支持。
- **修改与优化**：
  - **巨类拆解与双空间 MVVM 统一**：移除原先 4,088 行的 `StudyFragment` 与 970 行的 `ProfileFragment`，拆分为职责单一的子页面与控制器；新增 `StudySharedViewModel`、`LifeSharedViewModel` 与 `ExchangeViewModel`，淘汰裸线程池并统一为生命周期感知协程 IO。
  - **长列表性能升级**：学习待办草稿本、学习记录搜索抽屉、借钱按日流水及 24 币种汇率列表全面升级为 `RecyclerView + ListAdapter + DiffUtil`。
