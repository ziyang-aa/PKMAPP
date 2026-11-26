# 小松果（原芽叶记 / PKMAPP）项目 Agent 规范

本文件为 `d:\pkmAPP` 仓库的常驻工作区规则。所有对本项目的需求设计、界面改造、代码实现与实机验收，必须严格遵循以下工程与设计规范（视觉 Token 细节同步参见 `DESIGN.md`）。

---

## 1. 技术栈与架构演进规范

- **渐进式现代 Android 架构（Java 11 + Kotlin 混编，禁止推倒重写）**：
  - 存量稳定的纯 Java 核心逻辑、编解码器与 14 个自绘 `Canvas` 视图（如 `StudyClockFace`、`StudyChartView`、`StudyHeatmapView`、`StudyGardenView`、`ForestCalendarView`、`ForestNotebookPad`）保持直接复用，不作无意义的语言重写。
  - 长期重构与新增功能严格遵循 [docs/REFACTORING-PLAN.md](docs/REFACTORING-PLAN.md) 的分阶段路线：优先使用 **Kotlin + Coroutines/StateFlow + Jetpack ViewModel + Room + Preferences DataStore**；暂不引入跨端框架。
- **双空间产品架构**：
  - 入口：`HomeFragment`（森林小窝双门分流），启动 Activity 为 `.SplashActivity`（`.MainActivity` 非 exported，adb 拉起时一律使用 `com.example.pkmapp/.SplashActivity`）。
  - **生活小屋**：`DetailsFragment`（明细）、`ChartsFragment`（图表）、`RecordFragment`（记一笔）、`SavingsFragment`（攒钱）、`ProfileFragment`（我的），以及二级工具页 `ExchangeFragment`（汇率换算，支持 Frankfurter v2 24 种 ECB 货币批量同步与本地缓存）、`BorrowingFragment`（借钱统计）。
  - **学习小屋**：`StudyFragment` 承载 `STUDY_FOCUS`（专注自习室）、`STUDY_TODO`（待办）、`STUDY_STATS`（学习足迹）、`STUDY_GARDEN`（我的小林地）。
- **动态资源保护**：`CategoryIconResolver` 通过 `Resources.getIdentifier("norm_type_...", ...)` 动态加载分类图标。任何清理冗余资源的操作前，必须先全局扫描 `getIdentifier` 及动态拼接名称，禁止误删 `norm_type_*` 等动态引用资源。

---

## 2. UI 视觉与反模板化核心原则（强约束）

> 核心气质：**静谧奢华暖纸手账（Quiet-Luxury Warm Paper & Editorial Hairline）**——简约、直白、克制、零视觉噪音。

1. **严禁盲目堆砌多纯色卡片与「卡片套卡片」**：
   - **禁止**把普通筛选条件、提醒状态、开关、输入框或说明区块随手包成纯色/绿底/黄底圆角盒子。
   - **禁止**在卡片内部或弹窗内部再套一层带边框/底色的子卡片（卡片套卡片）。
   - **禁止**用 `layout_weight="1"` 把几个字的短标签强行拉成空荡的宽条色块。
2. **优先采用「报刊发丝线风（Editorial Hairline）」组织层级**：
   - 顶部工具区、筛选区、统计周期切换和表单分组，优先用**无框文字 + 细线图标（`ic_editorial_*`）+ `0.75dp` 暖棕发丝线（`0x1f5c4938`）+ 选中短下划线**来建立层级，让暖纸背景与水彩叶片自然呼吸。
   - **避免在工具栏随意使用厚重纯色实心按钮**：工具栏操作（如「新建」「补记学习」）优先使用**暖瓷白底 `#FFFEFA` + 墨绿细描边 + 细线图标**的轻盈胶囊（严格按文字 `wrap_content` 收口），深松绿实心按钮（`#456444`）仅保留给页面最核心的提交/执行动作（如专注计时「继续学习」、弹窗底部「保存」），且单屏最多 1 个。
   - 严禁把两块以上通栏圆角条上下堆叠（例如长条绿框 + 灰色分段槽叠罗汉）。
3. **色彩与字体约束**：
   - 底纸 `#F5F1E7`，瓷白纸面 `#FFFEFA`，主按钮松绿 `#456444`，墨绿标题 `#315E3A`，深绿正文 `#243624`，暖木棕副文 `#7B5C3B`，陶红警示/逾期/借出 `#9E4C37`。
   - 页面大标题、核心数字、计划主标题使用系统衬线体（`serif`）；所有计时、金额、汇率、日期、统计数字必须开启 `setFontFeatureSettings("tnum")` 等宽数字防抖动。
4. **禁止在直观入口下方堆砌解释性小字**：
   - 凡是一眼就能看懂的功能入口、菜单行或开关（如「生活小屋」「多账本管理」「意见反馈中心」「记住上次进入的小屋」），保持清爽单行，**严禁**在下方硬加一排解释用途的小字或技术术语（如 `local_guest`、`Room Offline-First`）。
   - 仅当功能确实较复杂或存在关键策略差异（如导入备份的合并/覆盖规则）时，才按需保留简短克制的一行注释。

---

## 3. 三图层通栏与顶栏统一规范

- **严格三图层 Edge-to-Edge（禁止状态栏割裂）**：
  - **第一层（底层）**：全屏通栏背景图（铺满状态栏与手势区）。
  - **第二层（中层）**：`fragment_container` 保持 `topMargin = 0` 全高不裁切；内部滚动容器统一设置 `clipToPadding = false`，通过 `bars.top` 顶部内边距和 `bars.bottom + 112dp` 底部内边距预留安全区，确保滑动时页面元素顺滑从状态栏图标后方移过，绝不出现顶部横切割裂线。
  - **第三层（顶层）**：悬浮毛玻璃底部导航栏（`FrostedNavigationBar`）。
- **一级页面顶栏唯一标准**：
  - 生活小屋与学习小屋所有一级页面顶栏统一为：**左侧 `56dp × 56dp` `return_home_door` 返回门（`CENTER_INSIDE`，`padding=0`） + 右侧仅放 `24sp` 粗体右对齐页面标题**，二者垂直居中平行对齐。
  - **严禁**把缩小的功能按钮或筛选胶囊塞进右上角与标题挤在一起（用户不易发现且难按）；所有功能控件放在顶栏下方自然视线区，触控热区不小于 `36dp–48dp`。

---

## 4. 弹窗、表单与日期组件规范

- **统一暖瓷白底抽屉**：所有新建、编辑、筛选、提醒设置、借钱记账与币种选择弹窗统一使用 `StudySheet` 或 `ThemeOverlay_PkmAPP_StudySheet`（暖瓷白 `#FFFEFA` + 顶部小握把 + 右上圆形关闭键）。
- **零套框直排表单**：表单项直接落在瓷白弹窗画布上，用 `0.75dp` 发丝线与底线输入（`StudySheet.styleQuietInlineField`，静止态无框、聚焦态松绿底线）分隔，避免出现双线叠加。
- **表单信息组织**：
  - 关联字段相邻摆放（例如「学习手记」与「下次从哪继续」不人为拆散）。
  - 开关类设置实行**渐进式披露**（关闭主开关时自动收起下方时间预设与测试项）。
- **禁止调用系统原生丑陋日历**：严禁使用 `MaterialDatePicker` / 系统 `DatePickerDialog`；全项目日期选择与待办按日期筛选统一复用自绘瓷白日历 [ForestCalendarView.java](app/src/main/java/com/example/pkmapp/forest/ForestCalendarView.java)（支持待办/逾期/已完成三色圆点标记）。

---

## 5. 交互细节与状态稳定性规范

- **高频交互禁止整页闪刷**：专注计时器的暂停/继续、倒计时切换等状态变更必须原地更新控件（如 `syncRunningStateInPlace`），严禁触发整页 `render()` 重建导致卡片闪烁。
- **待办三分区互斥架构**：固定为 `今天`（含已逾期置顶提示） / `计划`（未来按日排布 + 随时可做收集箱） / `已完成`（严格按实际完成日期倒序分组的日志本）+ 单日筛选翻页条，避免出现语义重叠的「全部」页签。
- **测试契约保护**：改动布局与交互时须保留关键视图 ID 与自动化测试锚点文本（如 `R.id.page_back_button`、`R.id.study_wallet`、`R.id.study_date_today/week/month`、`"当前计划 · <title> ▾"`、`"补记学习"`、`"所有计划 ▾"`），防止破坏既有 `androidTest`。

---

## 6. 开发流程、实机验收与上下文/仓库纪律

- **复杂视觉改动先出 HTML 原型**：涉及页面重设计或多种布局权衡时，先输出可交互 HTML 设计稿供用户定夺，确认方案后再落地原生 Java/XML。
- **分级验证与编译门禁（避免频繁全量冷启动）**：
  - 连续编码过程中**不要每改一处就跑全量 `--no-daemon` 门禁**；局部调试优先使用开启 Gradle Daemon 的增量检查（如 `.\gradlew.bat compileDebugJavaWithJavac --console=plain` 或单类测试），避免超 10 秒转后台并频繁注入异步系统通知。
  - 仅在阶段/功能闭环收口、交付验收前，在项目根目录执行一次完整门禁（常规复用 Daemon；仅当需清理残留进程时加 `--no-daemon`）：
    ```powershell
    .\gradlew.bat testDebugUnitTest compileDebugAndroidTestSources assembleDebug --console=plain
    ```
    （Windows PowerShell 5.1 环境禁止使用 `&&`，请使用 `;` 分隔命令。）
- **UI 实机截屏验收（子代理隔离，严禁主对话吞图）**：
  - 实机验收放到**阶段收口时集中进行一次**，禁止每调一个小样式就在主对话中用 `read_file` 反复读取过程截图（图片会永久滞留主历史并急剧撑爆上下文）。
  - 优先将「安装 Debug APK → 模拟器操作核心路径 → 截屏逐张自检（重点排查：**文字裁切、正负号重复、状态栏割裂、双底线、多余纯色框/套框、按钮难以发现**）」委派给独立子代理执行；**子代理只返回精简的文本缺陷清单**（页面/控件位置 + 问题），主对话仅在确认修复后按需抽查终版截图。
- **轻量交接与上下文控制纪律**：
  - 每个 Phase 结束、跨模块切换（如 Android ↔ `pkmapp_wx`）或当前会话上下文接近 **200K** 时，顺手更新简短的 `docs/HANDOFF.md`（控制在 80 行以内，只记：当前进度、刚修改的关键文件、已通过验证、下一步待办），并提示用户开启新会话。
  - 新会话启动时优先只读 `AGENTS.md` 与 `docs/HANDOFF.md`（`DESIGN.md`、`docs/REFACTORING-PLAN.md` 按需检索局部章节），避免开局无差别全量读完所有长文档。
  - **代码探索优先用 CodeGraph（不禁止 `read_file`）**：仓库已建有 `.codegraph/` 索引，查找 Java/Kotlin 类方法、调用链与测试影响面时优先用 `codegraph_explore`；阅读 `docs/*.md`、`res/` 布局资源、Gradle 配置，以及大类改代码前核对精确行号上下文时，照常使用 `read_file` 精读局部范围。
  - 单步避免一次性生成过多大文件（建议拆批 `write_file`），降低超长输出中途断流重试概率。
- **仓库清洁与 Git 纪律**：
  - 临时验证脚本、截屏与 HTML 原型稿统一存放在对话 artifact / scratch 目录，**严禁**写入仓库根目录或提交进 Git。
  - 未经用户明确许可，禁止执行 `git commit` 或 `git push`；不得随意删除本地忽略的资产目录（如 `pkmapp_wx/`、`宝可梦账本素材/`）。
  - 推送 GitHub 若遇 22 端口超时，可使用 SSH 443 通道：`git -c "core.sshCommand=ssh -o Hostname=ssh.github.com -o Port=443" push origin <branch>`。
