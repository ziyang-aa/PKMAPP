---
version: alpha
name: "芽叶记 · Android"
description: "森林手绘风格的个人生活工具，涵盖森林小窝、学习小屋、生活小屋和小林地。"
colors:
  canvas: "#F5F1E7"
  surface: "#FFFCF3"
  sheet: "#FDFAF0"
  input: "#F8F4E9"
  primary: "#456444"
  ink: "#315E3A"
  sheet-ink: "#283F2C"
  muted: "#736C5D"
  wood: "#7B5C3B"
  selected: "#E7EDDA"
  line: "#DDDCC9"
  danger: "#8D4836"
typography:
  headline:
    fontFamily: serif
    fontSize: 27px
    fontWeight: 400
  sheet-title:
    fontFamily: serif
    fontSize: 23px
    fontWeight: 400
  body:
    fontFamily: sans-serif
    fontSize: 15px
    fontWeight: 400
  action:
    fontFamily: sans-serif
    fontSize: 14px
    fontWeight: 400
  caption:
    fontFamily: sans-serif
    fontSize: 12px
    fontWeight: 400
  timer:
    fontFamily: sans-serif-light
    fontSize: 27px
    fontWeight: 300
    fontFeature: '"tnum"'
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  sheet-gutter: 20px
  touch-target: 48px
rounded:
  button: 12px
  field: 13px
  sheet-row: 16px
  sheet-top: 32px
components:
  forest-canvas:
    backgroundColor: "{colors.canvas}"
    textColor: "{colors.wood}"
  primary-button:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    typography: "{typography.action}"
    rounded: "{rounded.button}"
  secondary-button:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    typography: "{typography.action}"
    rounded: "{rounded.button}"
  sheet:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.sheet-ink}"
    typography: "{typography.sheet-title}"
    rounded: "{rounded.sheet-top}"
  sheet-row-selected:
    backgroundColor: "{colors.selected}"
    textColor: "{colors.ink}"
    rounded: "{rounded.sheet-row}"
  input:
    backgroundColor: "{colors.input}"
    textColor: "{colors.sheet-ink}"
    typography: "{typography.body}"
    rounded: "{rounded.field}"
  secondary-label:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.muted}"
    typography: "{typography.caption}"
  divider:
    backgroundColor: "{colors.line}"
  danger-action:
    backgroundColor: "{colors.sheet}"
    textColor: "{colors.danger}"
    typography: "{typography.action}"
---

# 芽叶记 Android 设计规范

## Overview

芽叶记是一套自己每天会打开的生活工具。森林小窝负责欢迎与分流，学习小屋强调计时、计划和记录，生活小屋强调金额、日期和账目，林地负责呈现成长和奖励。整体像一本常用的森林手账，奶油底纸、绿色墨水和带蜡笔颗粒的手绘插画共用一套视觉语言。

以后修改 Android UI 时，将本文件与目标页面的 Java 和 XML 一起交给设计或编码助手。色值取自现有实现，字号与间距用于新设计对齐。涉及局部优化时，以对应组件当前的尺寸和状态为准。

## Colors

奶油纸色铺底，纸白用于卡片，底部面板用略暖的纸色。松绿用于主操作，深绿用于标题、数字和图标，木棕和灰棕承担次级说明。浅苔绿配对勾、边框或标签表示选中状态。保留暗陶红给确认删除或清楚标出的错误状态。图表继续按科目使用颜色，并配科目名和数值。

## Typography

页面标题和摘要可用系统衬线体，正文、表单和导航用系统无衬线体。计时使用等宽数字特性，金额和时长与标签分出清晰层级。字号允许跟随 Android 字体设置，长科目名优先换行，必要时在详情里提供全称。

## Layout

- YAML 里的 `px` 对应设计预览单位。映射到 Android 时，尺寸用 `dp`，字号用 `sp`。
- 科目库、记录列表保持紧凑。用分组标题和间距区分操作层级，插画放在入口、空状态和奖励等有明确作用的位置。
- 页内内容滚动时，为状态栏与手势区留出安全距离。底部面板的内容可滚动，「保存计划」「加入书架」等主要操作固定在底部，并考虑键盘、短屏、大字模式和长列表。

## Elevation & Depth

使用三层结构。根布局绘制连续背景，页面内容在其上滚动，底部磨砂导航浮于内容之上。学习和生活两区沿用同一个磨砂导航类。Android 12 及以上可使用背景模糊，其他环境用半透明纸色延续层次。列表主要用底色、分隔线和文字层级组织，底部面板和导航承担最明显的悬浮层次。

## Shapes

按钮沿用柔和圆角，表单和清单条目按触控尺寸略放大。学习底部面板使用 32 dp 顶角。已有蜡笔轮廓适合专注卡片等少量强调面，原有伙伴和树苗插画自然进入场景构图。

## Motion & Micro-interaction

沿用按下缩放和回弹、涟漪及轻触反馈，视觉反馈覆盖实际可点击区域。共用按压动画按下时约 75 ms，松手恢复约 150 ms。底部面板内部前进与返回用约 8 dp 位移和 145 ms 淡入提示方向，曲线为 `cubic-bezier(0.2, 0, 0, 1)`。选中状态同时有静态标记。遵循系统动画设置。林地的种植、浇水和成长用既有动画表现操作结果。

## Voice & Content Strategy

沿用「学习计划」「科目书架」「学习足迹」「小林地」等产品用语。按钮直接写结果，如「保存计划」「加入书架」「确认删除」。空状态给出可以开始的操作，错误提示说明对应字段或处理方式。删除计划前展示受影响的记录数，确认和保留各有清楚的入口。

## Components

| 组件 | 使用方式 | Android 实现 |
| --- | --- | --- |
| 按钮、色值与按压 | 主操作用绿色实底，次操作用纸色或文字。选中、禁用和按下都要能识别。 | `@app/src/main/java/com/example/pkmapp/forest/ForestUi.java` · `@app/src/main/java/com/example/pkmapp/forest/ForestButton.java` · `@app/src/main/java/com/example/pkmapp/forest/ForestMotion.java` |
| 底部面板 | 学习计划、选科、记录详情使用同一面板内的返回栈，区分「上一步」和「关闭」，保留草稿和筛选。 | `@app/src/main/java/com/example/pkmapp/study/StudySheet.java` · `@app/src/main/java/com/example/pkmapp/study/StudyPlanDialogs.java` · `@app/src/main/res/values/forest_dialog.xml` |
| 底部导航 | 同一标签的图标、文字、按压和选中区域对齐。 | `@app/src/main/java/com/example/pkmapp/forest/FrostedNavigationBar.java` · `@app/src/main/java/com/example/pkmapp/MainActivity.java` |
| 学习图表 | 环形图用引线标注科目和实际时长；科目汇总显示时长与占比，按占比降序排列。 | `@app/src/main/java/com/example/pkmapp/study/StudyChartView.java` · `@app/src/main/java/com/example/pkmapp/study/StudyFragment.java` |
| 手绘卡片与林地 | 用纸纹、蜡笔边角和少量插画组织重点。林地展示树苗、成长进度和浇水反馈。 | `@app/src/main/java/com/example/pkmapp/forest/CrayonCard.java` · `@app/src/main/java/com/example/pkmapp/study/StudyGardenView.java` |

## Do's and Don'ts

- 新版面先标清主操作、辅助操作、当前状态和空状态。触控控件以 48 dp 为目标，文字与所在底色按 WCAG AA 检查，图表和选中状态配文字或符号。
- 视觉原型可用现有 HTML 对照构图，原生 Android 的可用状态以实际组件为准。复杂调整可以先出原型，确认后落到对应页面。
- 参考外部样例时，选定想借鉴的一条原则，再用芽叶记的色字、插画和组件表达。全局设计规则达成确认后同步到本文件与共用组件。

## 后续使用

> 请先读取根目录的 DESIGN.md。优化安卓学习小屋的期末科目库，重点调整文科／理科入口层级和专业列表密度。先从 StudySheet 和 StudyPlanDialogs 中找可复用的组件，按设计规范处理选中、空状态和返回。

> 请先读取根目录的 DESIGN.md。参考 Oura 样例中「大数字、克制的辅助文字」的层级，优化学习足迹的累计时长卡片。将改动限定在该卡片，沿用芽叶记的纸色、深绿和时长格式。

需要比较外部样例时，从仓库下载对应的 `DESIGN.md` 到参考目录，用提示词说明借鉴点。项目根目录保留芽叶记这一份作为使用入口。样例可参考 Oura 的纸色与排版，以及 NerdWallet 的数字密度与分隔线。 [VoltAgent 样例目录](https://github.com/VoltAgent/awesome-design-md) · [Oura 样例](https://github.com/VoltAgent/awesome-design-md/blob/main/design-md/oura/DESIGN.md) · [NerdWallet 样例](https://github.com/VoltAgent/awesome-design-md/blob/main/design-md/nerdwallet/DESIGN.md)

调整色值、字号或组件引用后，可在 PowerShell 中运行下面的格式检查。 [Google Labs DESIGN.md CLI](https://github.com/google-labs-code/design.md#cli-reference)

```powershell
npx --yes -p "@google/design.md" designmd lint DESIGN.md
```
