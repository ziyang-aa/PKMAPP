# PKMAPP（芽叶记）

宝可梦森林绘本风格的个人记账 Android 应用，使用 Java 和 XML 开发。

## 项目功能

- 森林绘本风格的横向首页，以及固定的五项底部导航。
- 支出和收入分类记账，支持备注、日期、自定义分类和四则计算器。
- 多账本创建与切换、按日期分组的交易明细、本月收入/支出/结余统计。
- 收支趋势图表、周/月/年统计和分类排行。
- 攒钱目标、存入金额和目标进度。
- 借入/借出记录、联系人、日期和借钱统计。
- 汇率换算，支持联网刷新和上次成功汇率缓存。
- 个人资料、昵称、头像裁剪、每日签到和资产管理。

## 技术结构

```text
app/src/main/java/com/example/pkmapp/
├─ data/       账本、交易和本地数据管理
├─ home/       首页森林场景
├─ details/    交易明细和账本切换
├─ record/     记账、金额解析和计算器
├─ charts/     图表统计和趋势绘制
├─ savings/    攒钱目标
├─ borrowing/  借钱/借出记录
├─ exchange/   汇率换算
├─ profile/    头像、昵称、签到和资产
└─ navigation/ 页面导航
```

- `MainActivity` 是页面容器，负责底部导航和 Fragment 切换。
- `SplashActivity` 是启动画面。
- `app/src/main/res/layout` 存放 XML 页面布局。
- `app/src/main/res/drawable` 存放图标、按钮和背景样式。
- `app/src/main/res/drawable-nodpi` 存放森林背景、角色和分类图片。PNG 和 WebP 可以混用；大图优先使用无损 WebP 或经过检查的高质量 WebP。
- `app/src/test` 存放不需要手机的单元测试。
- `app/src/androidTest` 存放需要模拟器或真机的 Android 测试。

## 数据保存方式

项目目前不使用云端账号和数据库，数据保存在手机本地：

- 账本和交易：应用私有的 `SharedPreferences`。
- 攒钱目标、借钱记录和资产：各自的本地存储。
- 用户头像：应用私有文件目录。
- 汇率：联网获取，并缓存最近一次成功结果。

卸载 App 或清除 App 数据会删除这些本地数据。

## 构建与测试

Windows PowerShell 可以在项目根目录执行：

```powershell
./gradlew.bat testDebugUnitTest compileDebugAndroidTestSources assembleDebug
```

如果已经连接 Android 模拟器或真机，还可以执行：

```powershell
./gradlew.bat connectedDebugAndroidTest
```

调试 APK 生成在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub 上传说明

应该上传：

- `app/src` 中的 Java、XML、图片和测试代码。
- `build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`。
- `gradle/`、`gradlew`、`gradlew.bat`。
- `README.md`、`.gitignore` 和 `app/proguard-rules.pro`。

不应该上传：

- `build/` 和 `app/build/` 生成的构建产物。
- `.gradle/`、`.idea/`、`local.properties` 等本机配置。
- 开发过程中的 `build-*.png` 截图和模拟器临时文件。
- 含有密码、密钥、令牌或个人路径的配置文件。

本仓库会保留能让其他人从源码重新构建 App 所需的代码、资源和 Gradle 配置，不会把本机生成文件一起提交。
