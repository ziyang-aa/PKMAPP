# PKMAPP Figma 前端重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Figma 建立森林绘本风的 PKMAPP 设计系统和六张可供 Android Java/XML 实现的页面画板，并接入记账类别资源。

**Architecture:** Figma 保存命名颜色变量、间距规则和可复用视觉组件，页面以 390 × 844 Android 画板承载。Android 工程中的 WebP 仅承担本地资源，不与 Figma 的页面结构耦合；最终通过 Figma 截图与节点结构进行视觉校验。

**Tech Stack:** Figma Plugin API、Figma Material 3 navigation component、Android Java/XML、透明 WebP drawable-nodpi 资源。

**Spec:** `docs/superpowers/specs/2026-09-07-figma-frontend-redesign.md`

## Global Constraints

- Figma 文件键为 `WwdeQmYQQQ9afd9cKo2MHZ`，目标画布为 Android 390 × 844。
- 只使用用户提供的角色和类别插画；操作图标使用统一线性矢量图标。
- 全部可操作热区不少于 48dp，固定导航和操作条必须避开系统安全区域。
- 不在本阶段新增后端、数据库或同步逻辑。

---

### Task 1: 资源标准化与 Figma 基础令牌

**Files:**
- Create: `app/src/main/res/drawable-nodpi/ic_category_*.webp`
- Create: Figma collection `PKMAPP/Colors` 与 `PKMAPP/Layout`

**Interfaces:**
- Consumes: `宝可梦账本素材/装饰素材/记账类型按钮/{收入,支出}/*.png`
- Produces: 22 个透明 WebP 资源及用于 Figma 页面创建的颜色/间距令牌。

- [ ] **Step 1: 检查所有 PNG 是否透明并记录尺寸**

  Run: `py -3 - < inspect_category_assets.py`

  Expected: 22 个 `RGBA` 文件，且透明通道存在。

- [ ] **Step 2: 转换和裁切资源**

  将透明区域裁切后居中为最大 512px WebP；文件名使用 `ic_category_expense_*` 与 `ic_category_income_*`。

- [ ] **Step 3: 验证转换结果**

  Run: `py -3 - < verify_webp_assets.py`

  Expected: 22 个非空 WebP，带 Alpha 通道，边长不超过 512px。

- [ ] **Step 4: 在 Figma 创建命名颜色和布局变量**

  创建纸张、森林绿、叶绿、木棕、暖橙、收入绿、支出红与主要/次要文字变量；创建 4/8/12/16/24/32 的间距变量。

- [ ] **Step 5: 验证 Figma 变量集合与资源计数**

  用 Figma metadata 和变量读取确认集合名、变量名与资源数量。

### Task 2: 核心导航和记账录入画板

**Files:**
- Create: Figma frames `PKMAPP / 主页面`、`PKMAPP / 记账-类别`、`PKMAPP / 记账-录入`

**Interfaces:**
- Consumes: Task 1 的颜色、间距、类别插画。
- Produces: 可复用的底部导航、页面标题、类别圆形按钮以及记账渐进流程。

- [ ] **Step 1: 创建 390 × 844 主页面画板和固定五导航**

  画板底部包含 明细、图表、记账、攒钱、我的 五项；创建长场景图片区作为后续三只角色背景插槽。

- [ ] **Step 2: 创建类别选择画板**

  默认支出选中，创建收入/支出切换与四列类别圆形按钮布局；类别文字置于图标下方。

- [ ] **Step 3: 创建类别确认后的录入画板**

  保留选中的类别，显露金额、备注、年月日滚轮和“完成”主按钮，不在类别选择首屏显示完整计算器。

- [ ] **Step 4: Figma 截图校验**

  Run: Figma `get_screenshot` for each core frame.

  Expected: 文字未裁切、底栏不覆盖内容、类别操作按钮具有明确选中状态。

### Task 3: 明细与图表画板

**Files:**
- Create: Figma frames `PKMAPP / 明细`、`PKMAPP / 图表`

**Interfaces:**
- Consumes: Task 1 令牌和 Task 2 导航/标题视觉规则。
- Produces: 可被现有 DetailsFragment 和 ChartsFragment 映射的页面布局。

- [ ] **Step 1: 创建明细页层级**

  放置返回门插画、右侧标题、账本/月选择、结余卡、工具卡与按日期分组的交易列表。

- [ ] **Step 2: 创建图表页层级**

  放置返回门插画、收入/支出分段按钮、周/月/年切换、单类型折线图和排行榜。

- [ ] **Step 3: Figma 截图校验**

  Run: Figma `get_screenshot` for 明细 and 图表.

  Expected: 数据层级先于装饰，折线与排名不只依靠颜色表达类别。

### Task 4: 攒钱、我的与 Android 映射交接

**Files:**
- Create: Figma frames `PKMAPP / 攒钱`、`PKMAPP / 我的`
- Modify: `docs/HANDOFF.md`

**Interfaces:**
- Consumes: 前三项页面的视觉语言。
- Produces: 完整六屏 Figma 设计和 Android 实现交接说明。

- [ ] **Step 1: 创建攒钱页面**

  为旅游、恋爱等目标提供进度卡、目标金额、进度条与创建入口。

- [ ] **Step 2: 创建我的页面**

  提供资产总览、头像、昵称及设置列表，不使用不可识别的图标作为唯一说明。

- [ ] **Step 3: 进行完整截图和结构核对**

  Run: Figma `get_metadata` and `get_screenshot` on all six frames.

  Expected: 每张画板 390 × 844，底部导航与安全区域一致，所有标题、卡片与按钮完整可见。

- [ ] **Step 4: 更新交接文档**

  记录 Figma URL、画板节点 ID、颜色令牌、WebP 文件名、尚未进行的 Android XML 重构与验证方式。

- [ ] **Step 5: 提交阶段成果**

  ```bash
  git add app/src/main/res/drawable-nodpi docs/HANDOFF.md docs/superpowers
  git commit -m "design: add figma frontend specification"
  ```
