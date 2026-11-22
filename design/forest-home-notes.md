# 森林生活集合 · 入口与导航概念稿 03

打开 [交互原型](study-forest.html)。推荐通过本地预览地址访问：http://127.0.0.1:4173/design/study-forest.html。

本轮仍是 HTML 方案调整，尚未改动安卓页面或小程序。安卓模拟器已启动用于后续对照。

## 入口：让伙伴带路，走进对应的小屋

把用户提出的“小屋大门”和“两位伙伴”合成一个入口场景：左侧学习小屋由松鼠伙伴“松栗”陪伴，右侧生活小屋由现有妙蛙种子素材“小芽”陪伴。伙伴昵称只是本稿的展示名，可随设计调整。

点击门牌或伙伴都进入对应模块。首页呈现两个目的地，不展示记账的底部导航，也不堆叠账本数据。以后增加其他生活功能，可以增加新的小屋或伙伴，不必把所有模块都塞进一排导航。

## 两层导航

- 森林入口：选择学习小屋、生活小屋，不设模块标签栏。
- 学习小屋：专注、学习足迹、小林地。
- 生活小屋：明细、图表、记账、攒钱、我的，保留现有记账设计的五项导航。
- 两间小屋顶部都有一扇小门，可返回森林入口。

桌面设计板并排展示入口、学习与生活；手机默认展示入口，通过点击伙伴完成完整路径。

## 内容整理

学习首页移除了“今天的小脚印”和学习记录列表。首页保留科目、计时与今日目标，学习明细统一在学习足迹中查看。环形图引导线、具体科目时长和互动林地均保留。

离开学习小屋时，已开始的计时继续运行。入口显示正在进行的学习与计时状态，可一键返回；暂停后返回入口则显示已暂停。切换生活模块不会重开或丢失这段计时。

生活小屋复用原有 [记账视觉稿](pkmapp-visual-redesign.html)，通过原型容器调整尺寸、导航和返回路径，原文件未修改。复用页面的演示储蓄目标使用独立的预览存储键。这里展示的是已有 HTML 演示页面，不连接安卓本地账单。

## 本轮美术

使用内置 image_gen.imagegen 生图工具。两间小屋的场景与抱书松鼠为新生成素材；生活伙伴沿用项目现有的 balance_bulbasaur_coin.png。

| 素材 | 原型文件 | 原图存档 |
| --- | --- | --- |
| 双小屋森林场景 | design/assets/forest_two_cottages.png | artwork/source/backgrounds/bg_forest_two_cottages.png |
| 抱书学习伙伴 | design/assets/study_squirrel_reading.png | artwork/source/characters/study/char_study_squirrel_reading.png |

小屋场景最终提示词：

> Use case: illustration-story. Asset type: portrait background for a personal Android multifunction woodland life app home screen. Primary request: a peaceful forest clearing containing two small inviting wooden cottages, one on the left with a moss-green roof, a round door and a stack of books visible near its window, the other on the right with a warm ochre roof, an arched door, a little herb garden and a terracotta pot. A gently forking stone path starts at bottom center and leads to both doors. Style: hand-painted watercolor and delicate colored pencil on warm cream paper, quiet children's forest storybook, muted sage and olive leaves, warm honey sunlight, natural fine outlines, no saturated neon colors. Composition: vertical 2:3, upper third is soft pale cream sky and sparse tree branches with open breathing space for a live heading; both cottages centered around the middle of the image, clearly separate doors, foreground in lower quarter is a softly painted meadow with tiny white daisies and ferns. Keep both doors fully visible. No characters, no text, no signage, no letters, no UI, no border, no watermark. This is scenery behind separately rendered clickable character sprites.

学习伙伴最终提示词：

> Use case: illustration-story. Asset type: one transparent character sprite for a woodland study app. Primary request: a friendly golden-brown squirrel with a moss-green knitted scarf sitting upright, gently holding an open cream-paged sage-green book in its two paws, looking toward the viewer with a small encouraging smile. Its fluffy curled tail rests to the left. Style: delicate watercolor and colored pencil children's woodland storybook, warm cream and muted sage palette, soft fine pencil outlines, visible watercolor grain, no thick cartoon outline. Composition: whole character including ears, feet, tail and book visible, centered in a square with modest safe margins. Genuine transparent background. No plants, no ground, no desk, no watering can, no other characters, no text, no letters, no border, no watermark. Sprite will be used independently as the study companion.

## 本轮验证

按实际交互路径检查：伙伴入口与门牌入口、模块导航切换、返回森林入口、学习记录只在足迹中出现、跨模块计时保留、原有记账分类进入录入、切换记账标签保留录入状态、学习奖励去林地时不会替换生活预览，以及 390px / 320px 窄屏排版。浏览器加载资源及运行错误也纳入检查。

## 门牌融入场景的调整

两间小屋的名称改为直接画在门楣上的木质吊牌，不再显示浮在路径上的白色标签。门牌与门口保留透明点击区域，键盘焦点和无障碍名称仍可使用。“今天，想和谁一起出发？”移除底色、圆角与内边距，放到主标题上方的留白，替换原来的英文装饰行。放大的两位伙伴仍保持透明背景。

新增素材由内置 image_gen.imagegen 编辑生成，旧图保留：

- 原型引用：design/assets/forest_two_cottages_signed.png
- 原图存档：artwork/source/backgrounds/bg_forest_two_cottages_signed.png

最终提示词：

> Use case: precise-object-edit. Edit target: the supplied portrait watercolor background of two forest cottages. Change only the following small architectural details, preserving the exact image dimensions, cottage positions, roofs, doors, windows, forest, palette, watercolor paper grain, path, daisies, lighting and perspective. Add a small rustic wooden name plaque physically attached to the wall or suspended on two short cords just above each cottage's door arch, under its eaves. LEFT cottage with the moss roof and green round door: one naturally weathered warm wood plaque with clearly painted Chinese text, exactly: 学习小屋. RIGHT cottage with the ochre roof and brown arched door: matching rustic wood plaque with clearly painted Chinese text, exactly: 生活小屋. Use neat hand-lettered dark brown Chinese characters large enough to read in a phone interface, four characters on one line on each plaque. Each plaque about the width of its own door, with subtly uneven wooden edges, tiny mounting nails or cords, gentle physically consistent shadow on the wall, and the same delicate watercolor pencil rendering as the buildings. They must look like part of the cottages, not floating UI labels, white rounded cards, stickers, or signposts in the foreground. Do not cover the door openings or windows. No other new objects, no other text, no symbols, no arrows, no decorative UI. Keep the upper pale sky blank for existing live app heading.


