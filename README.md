# PKMAPP · 芽叶记

芽叶记是一款宝可梦森林绘本风格的 Android 个人记账应用。以温暖的森林场景呈现日常收支、账本统计和攒钱进度，数据主要保存在设备本地。

## 功能介绍

- **日常记账**：记录收入和支出，选择分类、日期和备注，支持自定义分类与四则计算器。
- **多账本管理**：创建、切换账本，按日期查看交易明细，统计每月收入、支出和结余。
- **收支分析**：按周、月、年查看趋势和分类排行。
- **攒钱目标**：设置目标金额、记录存入金额并查看完成进度。
- **借款记录**：管理借入、借出记录及联系人，查看借款统计。
- **汇率换算**：通过 Frankfurter API 获取汇率，缓存最近一次成功结果。
- **个人页面**：修改昵称、裁剪头像、每日签到和管理资产。
- **森林主题**：横向森林全景首页、角色插画与统一的纸张质感界面。

## 技术栈

| 领域 | 技术与用途 |
| --- | --- |
| 开发语言 | Java；Gradle 构建配置使用 Kotlin DSL |
| 界面 | Android XML 布局、ViewBinding、Material Components、AppCompat |
| 页面与列表 | AndroidX Fragment、RecyclerView |
| 图表 | 自定义 Android View 绘制收支趋势 |
| 本地存储 | SharedPreferences 与 JSON 序列化；头像保存在应用私有文件目录 |
| 网络请求 | HttpURLConnection、Frankfurter 汇率 API |
| 金额计算 | 整数分存储金额，BigDecimal 处理计算器运算 |
| 构建 | Gradle Wrapper 9.3.1、Android Gradle Plugin 9.1.1 |
| 测试 | JUnit 4、AndroidX Test、Espresso |

项目使用原生 Android 界面，未引入云端账号系统或 Room 数据库。Java 源码兼容级别为 Java 11，Gradle 构建使用 JDK 21。

## 环境与运行

- Android Studio（需支持 Android Gradle Plugin 9.1.1）
- JDK 21
- Android SDK 36（minor API level 1）
- Android 7.0（API 24）或更高版本的模拟器或真机

使用 Android Studio 打开项目根目录，完成 Gradle 同步后，选择 `app` 并运行到模拟器或设备。Android SDK 路径由本机 `local.properties` 配置，不需要提交到仓库。

也可以在项目根目录使用 Gradle Wrapper 构建：

```powershell
# Windows PowerShell
./gradlew.bat assembleDebug
./gradlew.bat testDebugUnitTest
```

```bash
# macOS / Linux
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。连接模拟器或真机后，可运行 `connectedDebugAndroidTest` 执行设备测试。

## 项目结构

```text
app/src/main/java/com/example/pkmapp/
├── data/        账本、交易与本地数据管理
├── home/        森林全景首页
├── details/     交易明细与账本切换
├── record/      记账、分类与计算器
├── charts/      收支统计与趋势图
├── savings/     攒钱目标
├── borrowing/   借入与借出记录
├── exchange/    汇率获取与换算
├── profile/     头像、昵称、签到与资产
└── navigation/  页面导航
```

`app/src/main/res/` 保存布局、样式及图片资源；`app/src/test/` 和 `app/src/androidTest/` 分别保存本地单元测试和设备测试。

## 数据说明

账本、交易、攒钱目标、借款记录和资产保存在设备本地，头像保存在应用私有目录。汇率功能需要联网，并会向汇率服务发送所选货币对；记账数据不会通过该汇率请求上传。

清除应用数据或卸载应用可能导致本地记录丢失，请在操作前妥善保存重要数据。
