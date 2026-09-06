# PKMAPP

宝可梦森林绘本风个人记账 Android 应用，使用 Java 与 XML 开发。

## 当前可体验内容

- 可左右拖动的横向主页面，以及固定的五项底部导航。
- 支出/收入分类记账：先选分类，再填写金额、备注与滚轮日期；支持自定义分类和四则计算器。
- 内存账本的新建与切换、本月收支结余、按日期分组的交易明细。
- 明细页的小工具入口：汇率换算和借钱统计的正式数据功能仍在后续阶段。

当前数据只保存在应用运行时的内存中；重启应用会重置记录。数据库、本地持久化、联网汇率、借钱联系人与记录、图表、攒钱和个人资料的数据功能均尚未完成。

## 构建与校验

在项目根目录执行：

```bash
./gradlew testDebugUnitTest compileDebugAndroidTestSources assembleDebug
# 当 WSL 可见的模拟器或真机已连接时：
./gradlew connectedDebugAndroidTest
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。
