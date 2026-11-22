# 森林生活集合 · HTML 概念稿 03

打开 [study-forest.html](study-forest.html) 即可体验。也可以在项目根目录执行 `python -m http.server 4173 --bind 127.0.0.1`，访问 <http://127.0.0.1:4173/design/study-forest.html>。

本轮交付是安卓生活集合的独立原型，还没有接入 Android。沿用现有奶油纸色、森林绿、木棕色和水彩森林画风。最新一版增加森林入口、学习与生活伙伴，以及两个模块各自的导航，详见 [入口与导航说明](forest-home-notes.md)。桌面并排展示总入口、学习小屋和生活小屋，手机从入口点击伙伴进入对应模块。

## 可以体验的内容

- 学习小屋：英语、高等数学、线性代数，以及数据结构、计算机组成原理、操作系统、计算机网络；正向计时、暂停、恢复、完成和学习手记。本次目标可以选 25 / 45 / 60 分钟，到达目标仍继续计时。首页已移除重复的小脚印记录，学习明细统一在足迹中查看。
- 学习足迹：今天、近 7 天、近 30 天；默认环形时长图，每段用引导线向外标注科目和具体时长，中心为合计。可切换科目横向条形和每日趋势。近 30 天的趋势按每 5 天一组显示。
- 我的小林地：实时二维互动场景，树苗待机轻摆；点击回应；浇水时水壶入场、倾斜洒水、根部涟漪、树苗舒展长大；成熟后收藏，再次播种会萌发。
- 每日目标可调整。记录与进行中的计时保存在当前浏览器；暂停时间不计入学习时长，刷新可以恢复。跨日的一次学习暂按完成日归档。
- 示例数据与空白体验使用独立存储。示例模式的“体验完成 25 分钟”会添加带有明确模拟手记的示例记录。切换为空白体验后，该按钮隐藏。

环形图按用户参考图的信息结构调整，保留森林配色：引导线外侧分两行显示科目与时长，不显示百分比。左右标签分别避让，未学习的科目在图下注明，不画没有时长的色块。中心显示总时长。横向条形图直接比较投入时长，每日趋势用于观察学习节奏。

动态林地的实现、适合安卓的技术路线及 GitHub 项目比较见 [study-animation-research.md](study-animation-research.md)。

## 奖励规则

每累计学习 25 分钟获得 1 滴水，跨次累计，在保存记录时结算。每天达到学习目标获得 1 颗种子，每天最多一次。首次进入空白体验赠送 1 颗种子；种树消耗 1 颗种子，浇水消耗 1 滴水，浇满 5 次收藏一棵树，再种下一棵。休息、暂停、未达目标都不扣奖励，小树不会枯萎。

## 美术素材

- `assets/study_forest_scene.png`：用户提供的森林插画，作为学习小屋场景。
- `assets/study_squirrel_watering.png`：本轮通过内置 imagegen 生图工具生成的透明水彩插画，1536 × 1024，已检查透明通道和页面效果。
- `../artwork/source/characters/study/char_study_squirrel_watering.png`：同一生成素材的高分辨率原图存档。
- `assets/study_watering_can.png`：第二版通过内置生图工具新增的透明水壶，作为可以独立移动、旋转的动画部件；生成提示词及原图位置见互动方案说明。
- 纸张纹理、动态树苗三个外形及收藏树缩略图复用安卓项目既有素材。第二版林地不再显示整张松鼠插画，原图保留用于后续角色参考。

生成工具：内置 `image_gen.imagegen`，参考图为用户本轮提供的森林插画，透明背景。最终提示词：

> Use case: illustration-story. Asset type: transparent cutout illustration for a personal Android forest-themed study tracking prototype. Reference image: attached forest watercolor scene, for style and character only. Primary request: a charming small golden-brown squirrel with a knitted moss-green scarf, gently watering a young leafy sapling from a small terracotta watering can. A closed sage-green study notebook sits beside its feet on a tiny mossy soil patch with two small daisies. Style: delicate watercolor and colored-pencil children's woodland storybook, warm cream and muted sage palette, fine natural pencil outlines, visible hand-painted texture, soft morning light. Composition: compact horizontal vignette, full character and sapling visible, generous clean margin on all edges. Transparent background, no full scenery, no text, no letters, no symbols, no UI, no border, no watermark. Preserve the warm botanical hand-painted feel of the reference. The squirrel is a friendly study companion.

## 本轮验证

已在浏览器验证 7 个科目、三种图表及日期范围、计时与暂停、运行及暂停状态刷新恢复、记录保存与手记转义、水滴结算、每日种子上限、成熟树收藏与重新种植、空白状态、窄屏导航和示例数据隔离。浏览器无控制台错误，JavaScript 语法检查通过。

第二版另验证了：时长精确到小时与分钟；实时场景帧更新、点击回应、浇水阶段与重复点击保护、刷新不重复扣水、动画结束后提示入藏、播种、减少动态效果和移动端导航。环形引导线在 320px、390px、七科、极端时长差异、单科及秒级记录下检查了标签边界与重叠。

下一阶段在页面方案调整后，再接入 Android 本地记录、后台计时和备份恢复。浏览器原型不能作为安卓锁屏和后台运行行为的验证结果。
