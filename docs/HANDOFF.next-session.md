# PKMAPP 新窗口开发交接

## 先看结论

这是一个使用 Java 和 XML 开发的个人 Android 记账应用。当前已完成可运行的森林绘本风前端、主要内存交互、联网汇率与借钱统计首版；交易主账本仍是内存仓储，借钱记录使用本地 JSON 持久化。按用户最新指令直接在 `D:\pkmAPP` 开发，阶段完成后更新本文件或 `docs/HANDOFF.md`；用户明确要求前不要提交或推送。

## 项目与 Git

- Git 仓库：`git@github.com:ziyang-aa/PKMAPP.git`
- 分支：`main`
- 当前 Windows Android Studio、开发与构建目录：`D:\pkmAPP`
- 历史 WSL 源目录：`/home/uug/projects/pkmAPP`；除非用户重新要求，不要切回 WSL。
- 当前最新已推送提交：`168cf12 feat: add chart trends and rankings`
- 用户的 Git 身份为 GitHub 同名信息，邮箱：`202421147163@njtech.edu.cn`。

直接在 `D:\pkmAPP` 开发和构建。不要重置、覆盖或删除用户已有改动。

## WSL 构建环境

- JDK 21：`/home/uug/.local/jdks/openjdk-21/usr/lib/jvm/java-21-openjdk-amd64`
- Android SDK：`/home/uug/Android/Sdk`
- `local.properties` 是本地配置，不要提交。

历史 WSL 环境仅供参考；当前 Windows 项目使用下面命令校验：

```bash
\.gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain
```

当前可用设备为 Android 模拟器 `emulator-5554`；`connectedDebugAndroidTest` 仍未作为本阶段命令运行。

## 当前已完成

- 固定五项底部导航：明细、图表、记账、攒钱、我的。
- 首页为可左右拖动的一张横向森林场景，底部导航保持固定；点击导航进入对应页面。
- 功能页左上角使用用户的木守宫开门 WebP 返回首页，已调整为紧凑点击范围。
- 明细：账本新建与切换、按月滚轮筛选、收支结余、按日期分组的交易列表、汇率与借钱统计入口。
- 图表：收入/支出切换、周/月/年切换、从当前内存账本交易动态聚合的原生折线趋势图及分类排行榜；点击有金额的曲线节点会显示日期、金额和该时间桶的交易笔数。
- 记账：默认支出、先选分类才显示输入区、支出/收入切换、金额计算器、备注、年月日独立下拉日期、自定义分类、保存到内存账本。
- 明细与记账已能在当前进程内联动。应用重启后数据会丢失。
- 攒钱目前为前端壳页，未完成正式数据功能；我的页面已完成森林档案与每日签到的前端交互，资产和设置数据仍是原型展示。

### 2026-09-08 记账页最新修订与模拟器验证

- 记账页收支切换已改为纸色分段控件：支出左侧为圆弧、靠收入侧为直线；收入状态镜像处理，尺寸按 HTML 设计稿放大。
- 每次记录只允许一个分类；重新选择分类会清除上一个分类的选中状态。已选区域显示分类插画和“已选择：分类名”。
- 发生日期改为计算器上方的年/月/日三段选择框，点击后分别打开独立的纸张风格下拉列表，年份限制单行显示；分类点击后会自动平滑滚动到计算器区域。
- 分类按钮统一为 116dp 高、64dp 插画区域，使用规范化裁切后的 `norm_type_*.png`，保持四列一致。
- 本地命令 `D:\pkmAPP\.\gradlew.bat assembleDebug --no-daemon --console=plain` 已成功；最新 APK 已安装到 `Pixel_10_Pro_XL`。
- 模拟器此前黑屏的根因是 AVD 图形渲染状态异常，不是 APK 崩溃；重启并指定 `-gpu swiftshader_indirect` 后首页和记账页均已正常渲染，分类点击进入计算器的视觉流程已确认。未提交、未推送。

### 2026-09-08 明细页视觉对齐与分类图标接入

- 明细页按 HTML 设计稿重排为账本入口、月份筛选、绿色余额卡、森林小工具和按日期归档的交易列表，去掉旧式 MaterialButton 外观。
- 交易列表每一行新增统一尺寸的插画图标，并通过 `CategoryIconResolver` 映射到 `app/src/main/res/drawable-nodpi/norm_type_*.png`；收入、支出及未知分类都有明确资源回退。
- 新增“本月”按钮，可把月份筛选恢复为系统当前年月；月份选择仍可继续按月过滤。
- `D:\pkmAPP\.\gradlew.bat testDebugUnitTest --tests com.example.pkmapp.record.CategoryIconResolverTest --tests com.example.pkmapp.record.RecordDateStateTest --no-daemon --console=plain` 通过；`D:\pkmAPP\.\gradlew.bat assembleDebug --no-daemon --console=plain` 通过。APK 已安装到 `emulator-5554`，实机截图确认明细页布局及餐饮、交通、网购插画正常显示。未提交、未推送。

### 2026-09-08 我的页面与签到弹窗视觉对齐

- 我的页面按 HTML 的“森林档案”重排：森林身份 Hero 使用 `bulbasaur_sleeping.webp`，资产总览采用单一大数字与双栏明细，签到入口使用轻量纸张行，设置区域改为无独立大卡片的分组列表。
- 签到弹窗改为 XML 布局的纸张底部弹窗，补齐 `DAILY FOREST LOG`、等级进度、周一开头日历、前后月日期、绿色已签到日期、图例和签到禁用态；不改变每天一次、不补签规则。
- 新增 `dialog_checkin.xml`、`bg_profile_*`、`bg_checkin_*`、`progress_checkin.xml` 及少量森林线性图标资源。已在 `emulator-5554` 视觉检查主页面和打开的签到弹窗。未提交、未推送。

### 2026-09-08 图表动态数据与节点明细

- Android 图表不再使用固定展示数组：当前内存账本会按支出/收入及周/月/年周期聚合金额，趋势总额、环比文案和分类排行榜同步使用同一周期数据。
- 每个有金额的曲线节点都支持点击，节点上方显示纸张风格提示框，包含具体日期、金额及该时间桶的支出/收入笔数；无交易的零值节点不显示圆点。
- 新增 `ChartDataCalculator.bucketCounts` 及对应 JVM 单测；`TrendChartView` 负责动态节点、标签和点击提示绘制。`testDebugUnitTest`、`assembleDebug` 均通过，并已安装到 `emulator-5554` 视觉确认 `9月3日 ¥88.80 · 1 笔支出` 提示框。
- 本阶段仅修改本地 Windows 副本，未提交、未推送；生成的 `build-charts-*.png` 截图保留在项目根目录，后续不要将其误删或批量加入提交。

### 2026-09-08 五个功能页背景强化

- 明细、图表、记账、攒钱、我的五个页面继续统一使用 `D:\pkmAPP\宝可梦账本素材\装饰素材\5个页面的固定背景\背景.png` 对应的 Android 资源 `bg_fixed_forest.png`。
- 去除了旧的高透明奶油遮罩，让原始水彩树叶纸张背景清晰可见；背景仍挂在各页面根部的 ScrollView/NestedScrollView 上，因此内容上下滑动时背景保持固定。
- `assembleDebug` 通过并安装到 `emulator-5554`，图表页已分别检查顶部与滚动后画面，底图位置保持不变。未提交、未推送。

### 2026-09-08 记账收录反馈与攒钱页

- 修复记账保存链路：当计算器输入了金额但没有先按 `=` 时，`RecordAmountResolver` 会直接计算当前表达式；有效记录写入当前 `InMemoryLedgerRepository`，随后弹出纸张风格的“收录成功”弹窗。
- 收录成功弹窗提供“继续记一笔”和“查看明细”两个动作；前者清空当前表单，后者进入已刷新数据的明细页。
- 攒钱页已完成首版：森林 Hero、两个默认目标、动态进度条、已存/还差金额、创建目标、存入一笔、目标完成态。`SavingsGoalStore` 使用 `SharedPreferences` 保存目标和进度，存入金额不会超过目标金额。
- 验证命令：`D:\pkmAPP\.\gradlew.bat testDebugUnitTest --tests com.example.pkmapp.record.RecordAmountResolverTest --tests com.example.pkmapp.savings.SavingsGoalTest assembleDebug --no-daemon --console=plain` 返回 `BUILD SUCCESSFUL`；APK 已安装到 `emulator-5554`。
- 模拟器验证：选择餐饮后输入 `5`（不按等号）并点击“收进森林账本”，确认出现“收录成功”；查看明细确认 `-¥5.00`。给“秋日旅行”存入 `100` 后显示 `已存 ¥2620.00`、`还差 ¥3380.00`，强制停止并重启后余额仍保留；logcat 未出现 `FATAL EXCEPTION`。
- 本阶段只修改本地 Windows 副本，未提交、未推送。`build-savings-page.png`、`build-record-success-dialog.png` 为未追踪视觉验证截图，不要批量加入提交。

### 2026-09-08 图表收支人物抠图替换

- 图表页收支两侧已改用 `D:\pkmAPP\宝可梦账本素材\装饰素材\支持与收入卡通人物\支出 .png` 和 `收入.png`，两张图四角透明，解决人物旁白色方块问题。
- Android 新增安全文件名资源 `chart_expense_character.png`、`chart_income_character.png` 并更新 `fragment_charts.xml`；原有未追踪 `turtwig_chart.png`、`treecko_chart.png` 未覆盖。
- HTML 图表页也改为引用同一素材目录；本次未同步 Figma。
- `D:\pkmAPP\.\gradlew.bat assembleDebug --no-daemon --console=plain` 返回 `BUILD SUCCESSFUL`，APK 已安装到 `emulator-5554`，模拟器截图确认两侧人物均无白底。未提交、未推送。

### 2026-09-08 HTML 明细视觉确认后同步 Android

- 用户确认 `design/pkmapp-visual-redesign.html` 的明细页方向后，Android `fragment_details.xml` 同步为账本、月份、本月同排的紧凑筛选区；绿色余额卡继续承担单一大金额层级；森林小工具收窄为轻量双列入口；交易列表移除外围大卡片并保留日期分组。
- `DetailsListItem` 的日期标题现在显示月日和“今天/昨天”上下文；交易行使用规范化分类插画，签名金额优先显示，收入/支出作为较弱的辅助标签放在金额下方。
- `MoneyParser.formatCents` 增加千位分隔，保持两位小数，统一明细、图表、攒钱等金额展示。
- TDD 红绿证据：`DetailsListItemTest`、`MoneyParserTest` 均先因新行为失败，再在实现后通过。完整 `testDebugUnitTest assembleDebug` 和 HTML 四项检查均通过。
- APK 已重新安装到 `emulator-5554`；原生截图 `D:\pkmAPP\build-details-native-grouped-ready.png` 已确认页面无启动黑屏且明细页布局符合当前 HTML 方向。本阶段未提交、未推送。

### 2026-09-08 借钱工具日历与主账本同步

- 借钱工具的发生日期已从三列老式滚动弹窗改为森林纸张风日历 BottomSheet：选择日期标题、选中日期与星期、月份前后切换、周一至周日六周网格、相邻月份淡化、绿色圆形选中日期、取消/确定按钮；没有今天/昨天/前天快捷按钮。
- 默认日期来自系统当天；日历内点击只改变暂选值，点击确定后才写回借钱表单。点击相邻月份日期会同步切换当前显示月份。
- 借钱工具独立录入时，借出会同步写入主账本 `EXPENSE / 借出`，借入会同步写入 `INCOME / 借入`；对方和备注写入主账本备注。记账页带来源交易编号进入借钱工具时不重复写入主账本。
- 新增 `BorrowingCalendarModel`、`BorrowingTransactionSync` 及对应 JVM 单测；完整 `testDebugUnitTest assembleDebug` 通过，APK 已安装到 `emulator-5554` 并视觉确认日历弹窗、圆形选中态、月份切换和确定后的日期更新。未提交、未推送；`build-borrowing-calendar*.png` 截图保持未追踪。

### 2026-09-08 明细页面月份选择器视觉调整

- 明细页面的月份查看弹窗已改为记账页面同款的无边框下拉文字列表：年份、月份仍分开滚动选择，选项取消圆角卡片背景，字号提高到 18sp，行高为 52dp。
- “FOREST CALENDAR”、标题及“年份/月份”标签同步放大；当前 2026 年与 9 月仅用森林绿粗体区分，保留关闭、取消、确认、滚动和月份筛选逻辑。
- 新增 `DetailsPickerLabels.pickerOptionText` 回归测试，确认月份的 0-based 值仍正确显示为 1月到12月。`testDebugUnitTest assembleDebug` 通过，APK 已装入 `emulator-5554` 并视觉确认弹窗布局。未提交、未推送；`build-details-month-dropdown-text.png` 保持未追踪。

### 2026-09-08 攒钱 Hero 与图表排行榜去卡片

- 攒钱页顶部绿色 Hero 卡调整为更紧凑的绘本构图：卡片高度调整为 232dp，标题与说明文字放大，草苗龟插画调整为 176dp，并在右侧加入浅绿色圆形光晕；攒钱目标卡和数据逻辑未改动。
- 图表页“分类排行榜”移除外层 `PaperCard` 背景、描边和内缩卡片感，改为直接落在森林纸张背景上；每个排行榜行复用明细交易列表的透明底与淡色底部分隔线，保留动态排序、分类插画、笔数、金额和现有交互。
- `D:\pkmAPP\.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain` 返回 `BUILD SUCCESSFUL`；APK 已安装到 `emulator-5554`。模拟器截图 `build-after-hero.png`、`build-after-ranking.png` 已确认两处实际排版。SDK XML version 4 仅为非致命工具警告。
- 本阶段仅修改 Windows 副本，未提交、未推送；不要批量暂存现有工作区文件或视觉截图。

### 2026-09-08 我的页面森林身份卡视觉修正

- 修正了上一阶段遗漏的“我的”页面绿色森林身份 Hero：卡片高度调整为 232dp，妙蛙种子插画调整为 200dp 并轻微下移，加入右侧大范围浅绿色光晕和右上小圆形光晕，对齐用户提供的设计稿构图。
- 仅修改 `fragment_profile.xml` 与 `bg_profile_hero.xml`，没有改动资产总览、签到、设置列表或任何数据逻辑。
- APK 重新构建并安装到 `emulator-5554`；截图 `build-after-profile-hero-final.png` 已完成模拟器视觉检查。`testDebugUnitTest assembleDebug` 返回 `BUILD SUCCESSFUL`，SDK XML version 4 仍只是非致命工具警告。
- 本阶段未提交、未推送；不要批量暂存现有工作区或视觉截图。

### 2026-09-08 森林档案字号、浮动记账按钮与启动图标

- “我的”页森林身份 Hero 的标题、身份名、生活天数和等级胶囊进一步放大，卡片高度保持 232dp，并保留右侧浅绿色森林光晕，减少左侧留白。
- 底部导航栏调整为 88dp；记账入口改为 60dp 的双层纸张圆环加号图标并上移 8dp，从导航卡上沿浮出，点击仍进入记账页。圆形视觉绘制在 icon 内，没有覆盖 MaterialButton 背景，避免 `MaterialButtonToggleGroup` 的背景冲突崩溃。
- 启动图标使用 `D:\pkmAPP\宝可梦账本素材\装饰素材\重新生成版\妙蛙种子记账插画生成.png` 作为透明前景，Android 机器人网格底图改为浅森林纸张色；没有删除原有密度图资源。
- `D:\pkmAPP\.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain` 返回 `BUILD SUCCESSFUL`，APK 已安装到 `emulator-5554`；清空 logcat 后强制重启，`FRESH_LOGCAT_FATAL: none`，主界面正常恢复。已检查 `build-after-profile-hero-final.png`、`build-after-record-nav-floating.png`、`build-record-nav-action.png`、`build-app-icon-home.png`。
- 本阶段未提交、未推送；不要批量暂存现有工作区或视觉截图。SDK XML version 4 仍只是非致命工具警告。

### 2026-09-08 应用命名与首页森林小窝背景

- 应用展示名称由 `pkmAPP` 改为“芽叶记”，AndroidManifest 继续通过 `@string/app_name` 读取，包名与内部 Java 包结构不变。
- 首页背景新增 `app/src/main/res/drawable-nodpi/home_panorama_replacement.png`，来源为 `D:\pkmAPP\宝可梦账本素材\装饰素材\主页面背景\主页面素材替换版.png`。首页仍保持横向全景平移，背景铺满页面并保留现有纵向固定场景逻辑；没有覆盖原 `home_panorama.png`。
- 新背景已经包含三只森林伙伴，因此移除了首页旧的三张独立角色叠加图，避免角色重复。首页增加轻量文案组：`FOREST NOTEBOOK`、`芽叶记`、`把每一笔生活，留在森林里` 和今日小目标提示；文案使用暖白/浅米色文字与木棕阴影提升背景上的可读性，没有新增功能卡片。
- APK 已构建并安装到 `emulator-5554`；启动后清空 logcat 检查为 `FRESH_LOGCAT_FATAL: none`，主界面正常进入。截图 `build-home-new-background.png` 确认首页视觉，`build-home-new-background-panned.png` 确认横向平移后仍显示连续背景。
- 本阶段未提交、未推送；不要批量暂存现有工作区或视觉截图。SDK XML version 4 仍只是非致命工具警告。

### 2026-09-08 首页文案对比度与应用名校正

- 按用户更正将应用展示名从“芽叶计”修正为“芽叶记”。
- 首页文案由森林绿/木棕改为暖白与浅米色，并统一使用木棕阴影色 `home_text_shadow`，解决文字与树屋木纹、叶片背景融为一体的问题；没有改变文字位置或首页交互。
- APK 已重新构建、安装并启动验证；`FRESH_LOGCAT_FATAL: none`。截图 `build-home-text-contrast.png` 已确认“芽叶记”及副文案在首页背景上清晰可读。
- 本阶段未提交、未推送；不要批量暂存现有工作区或视觉截图。

### 2026-09-08 首页横向拖动范围调整

- 主页面横向场景由约三屏扩大为四屏，主图继续使用 `centerCrop` 保持原比例；拖动范围已明显扩大，初始位置保留左侧构图，向右拖动可到达更接近素材右边界的位置。
- 首次进入主页面默认定位到全景中间；页面重建时继续恢复之前保存的横向位置。
- `D:\pkmAPP\.\gradlew.bat testDebugUnitTest --no-daemon --console=plain` 与 `D:\pkmAPP\.\gradlew.bat assembleDebug --no-daemon --console=plain` 均通过；APK 已安装到 `emulator-5554`，左右端视觉检查通过，日志无 `FATAL EXCEPTION`。
- 本阶段仅修改本地 Windows 副本，未提交、未推送。

### 2026-09-09 森林资产管理与资产卡视觉补充

- “我的”页的森林资产总览已从演示数字改为真实初始值：新账本默认初始资产、初始负债均为 `¥0.00`，移除了生产代码里的资产/交易演示种子数据。
- 新增 `AssetSnapshot` 与按账本隔离的 `AssetStore`：当前资产 = 初始资产 + 当前账本收入 - 支出；净资产 = 当前资产 - 负债。账本变化通过 `LedgerDataListener` 立即刷新 Profile 页面，初始资产与负债使用 `SharedPreferences` 保存。
- 资产总览卡新增“管理我的资产”入口和暖纸张森林风底部抽屉，抽屉展示当前资产、净资产、负债，并允许设置初始资产与初始负债；输入支持 `0` 或空值，负数和非法金额会在对应输入框提示。
- 资产卡背景改为浅绿渐变，加入 `D:\pkmAPP\宝可梦账本素材\装饰素材\重新生成版\宝箱.png` 与 `金币堆.png`；按用户最新反馈已移除资产卡上的木牌及“森林宝藏”文字，木牌素材文件保留但不再参与页面渲染，避免深色玻璃拟态和老式 Android 对话框。
- TDD/构建：`D:\pkmAPP\.\gradlew.bat testDebugUnitTest --tests com.example.pkmapp.data.AssetSnapshotTest --tests com.example.pkmapp.record.MoneyParserTest --tests com.example.pkmapp.data.InMemoryLedgerRepositoryTest assembleDebug --no-daemon --console=plain` 返回 `BUILD SUCCESSFUL`。全量测试仍有既有 `PanoramaSizingTest` 两项失败（实现为 3 倍、测试期望 4 倍），与本阶段无关，未修改主页面。
- 资产页零值卡片与管理底部抽屉已在模拟器检查；最后一次渐变/木牌尺寸调整只完成构建，因另一项任务同时占用模拟器，未再次抢占设备。APK 已重新安装到 `emulator-5554`，未发现新增 `FATAL EXCEPTION`。
- 本阶段未提交、未推送；不要批量暂存现有工作区或视觉截图。

## 设计原则和用户偏好

- 风格：森林绘本风，三只角色确定为妙蛙种子、木守宫、草苗龟；颜色以暖纸张、草木绿、木棕为主，避免旧式高饱和、密集卡片感。
- 推荐色板：`#F5F1E7`、`#FFF8DF`、`#4E8146`、`#A8D58C`、`#7B5C3B`、`#D48A4A`、`#C75B4B`、`#3B352C`。
- 用户希望后续重做页面美感，先有高质量 UI，再落后端；图标资源可后补。
- 用户不希望过程更新太频繁：按完整阶段汇报即可；每个阶段完成要更新交接文档。
- 页面仍以 Java/XML 实现，不要迁移到 Kotlin 或 Compose。

## 素材现状

- 用户素材根目录：`D:\pkmAPP\宝可梦账本素材`。
- Android 资源目录：`app/src/main/res/drawable-nodpi/`。
- 用户已提供透明的开门返回按钮并已在页面使用。
- 下列 22 个分类图片已转换为带 Alpha 通道的 WebP，当前在 Windows 副本中为未追踪文件，尚未纳入 Git：
  - 支出：`ic_category_expense_{clothing,custom,daily,dining,drinks,entertainment,fruit,gift,loan,medical,red_packet,relationship,repayment,shopping,snacks,study,subscription,transport}.webp`
  - 收入：`ic_category_income_{allowance,part_time,resale,salary}.webp`
- 下一位开发者先确认这些资源是否已同步到 WSL 项目；若没有，同步后用实际图替换当前分类占位矢量图，提交时一并加入版本控制。
- 透明小物继续使用透明 WebP；背景可用普通 WebP。不要把素材的生成水印处理为已获授权的官方图。

## 当前未提交文件和注意事项

Windows 副本目前有这些未追踪内容：22 个分类 WebP、`design/`、`docs/HANDOFF.windows-backup.md`、以及以下 Figma 规划文档：

- `docs/superpowers/specs/2026-09-07-figma-frontend-redesign.md`
- `docs/superpowers/plans/2026-09-07-figma-frontend-redesign.md`

这些不是本阶段已提交代码。不要盲目 `git add .`；先判断与当前任务是否相关。早期交接文档中提到的 `gradlew.bat` 和 `gradle/wrapper/gradle-wrapper.properties` 行尾变化也不要擅自还原或提交。

## Figma 状态

- Figma 文件：`https://www.figma.com/design/WwdeQmYQQQ9afd9cKo2MHZ/PKMAPP?node-id=0-1&p=f&t=f0y1rPS9BhpPuFRL-0`
- 文件最初是空白 `Page 1`。
- 官方 Figma MCP Starter/View 当前已触发限额，不能继续用其写入设计。不要反复调用消耗额度。
- 用户尝试过低星开源的 Figma Desktop Bridge（`figma-console-mcp`），后决定删除。此前自动删除因当前环境没有暴露桌面端/文件资源管理器窗口而未完成，仍需用户在本机手动完成：
  1. Figma Desktop：`Plugins → Development → Manage plugins`，移除 `Figma Desktop Bridge`。
  2. 资源管理器：将 `C:\Users\HP\.figma-console-mcp` 移入回收站。
- 不要使用该插件，不要索取或记录 Figma PAT。

## 接下来建议的开发顺序

1. 先重做 Android 前端视觉：建立轻量设计 token、统一标题栏/返回区、卡片、列表、分类按钮、图表和空状态；将新分类 WebP 接入记账页面。
2. 为攒钱页实现目标的 UI 工作流：创建、进度展示、编辑/删除确认；暂时可用内存数据。
3. 为我的页完善资产概览、头像与昵称编辑 UI；暂时可用内存数据。
4. 实现明细小工具的数据模型与 UI：借出/借入联系人、联系方式、记录与统计；汇率换算先做离线基础流程，再决定联网接口。
5. 用户确认最终视觉后，引入 Room 做本地持久化；再决定联网汇率自动更新。
6. 有设备后运行 `connectedDebugAndroidTest` 并做页面视觉验收。

## 必须遵守的工作方式

- 进行界面设计或修改时使用 `ui-ux-pro-max` skill；先阅读其 `SKILL.md`，但不要采用与用户风格冲突的深色玻璃拟态建议。
- 实现任何功能前使用 `superpowers:brainstorming` 和 `superpowers:test-driven-development`；遇到测试/构建异常，先使用 `superpowers:systematic-debugging`。
- 完成并声称成功前使用 `superpowers:verification-before-completion`，给出实际命令与结果。
- 使用 `apply_patch` 修改代码和 Markdown，避免覆盖用户改动。
- 先检查 Git 状态与现有文件，再开始改动。每一阶段：测试、更新交接文档、`git add` 精确文件、提交并推送到上述仓库。

## 可直接粘贴给新窗口的提示词

```text
请接手 D:\\pkmAPP 的 PKMAPP Android 项目。先完整阅读 D:\\pkmAPP\\docs\\HANDOFF.next-session.md 和 docs\\HANDOFF.md，再检查 Git 状态与最近提交，不要删除或覆盖未追踪文件。

这是 Java + XML 的个人宝可梦森林绘本风记账 App；实际开发、构建目录必须使用 WSL `/home/uug/projects/pkmAPP`，Windows 的 `D:\\pkmAPP` 仅供 Android Studio 拉取和运行。请按交接文件中的明确 JDK 21 命令构建。每个完整阶段完成后：更新交接文档、精确暂存相关文件、提交并推送到 git@github.com:ziyang-aa/PKMAPP.git；不要频繁汇报。

当前优先任务是重做前端视觉并接入已转换的分类 WebP：先使用 ui-ux-pro-max、superpowers:brainstorming、superpowers:test-driven-development 等适用 skill，保持暖纸张森林绘本风，避免深色玻璃拟态；不要切换到 Kotlin 或 Compose。完成后运行测试，汇报真实验证结果和下一步。
```

## Android Ledger Persistence and Exchange Refresh (2026-09-09)

- Main ledgers and transactions now persist in local `SharedPreferences` as JSON. `MainActivity` initializes the persistent repository before creating feature fragments; creating/switching ledgers and adding transactions immediately save the current state, including the selected ledger.
- Added `LedgerCodec` and a persistence-backed repository regression test that recreates the repository and confirms the saved transaction remains available. Test-only repositories remain in memory.
- Exchange-rate requests now disable HTTP caching and send `Cache-Control: no-cache` and `Pragma: no-cache`. Failed requests still use the last successful cached snapshot and clearly retain its date; successful responses display the date returned by Frankfurter. A direct request on 2026-09-09 returned `2026-09-09`.
- Verification: `D:\pkmAPP\.\gradlew.bat testDebugUnitTest assembleDebug --no-daemon --console=plain` returned `BUILD SUCCESSFUL`. ADB currently reports no connected device, so force-stop/restart and visual exchange checks remain pending.
- This phase remains local only. No Git commit or push was made.
