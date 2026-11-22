# 小林地互动动画 · GitHub 方案调研

调研日期：2026-11-17。已阅读下列仓库的 README；VS Code Pets 另检查了状态实现，Rive、Lottie、Godot、VS Code Pets 另核对了许可证。这里只比较与当前安卓个人生活 APP 有关的能力，没有引入这些仓库的源码或角色素材。

## 先推荐的方向

当前项目是 Java + XML，用户希望保留森林水彩画风。建议先走 **2D 分层手绘素材 + 互动状态机**：树苗待机摇动，触摸时回应，浇水时水壶移动并倾斜、出现水滴，之后树苗舒展，跨阶段切换外形，成熟时庆祝并入藏。像素、手绘、3D 是视觉选择；互动是否自然，还取决于动作和状态之间的衔接。

本轮 HTML 原型使用浏览器原生 Canvas 2D，已经实现上述交互的第一版。Android 正式开发可对应为自定义 `View`、`Canvas` 和按时间更新的状态机；先验证触摸反馈、帧率和生命周期，再决定是否需要引入 Rive。这个建议基于当前页面规模及 Java / XML 技术栈，是工程判断，不是仓库官方结论。原型没有加载第三方引擎或在线动画。

## 可参考的开源项目

| 项目 | 核实的能力 | 对本项目的价值与限制 |
| --- | --- | --- |
| [VS Code Pets](https://github.com/tonybaloney/vscode-pets) | 像素宠物，支持追球互动；[状态源码](https://github.com/tonybaloney/vscode-pets/blob/main/src/panel/states.ts)组织了待机、走路、奔跑、挥爪、追逐等行为。代码 [MIT](https://github.com/tonybaloney/vscode-pets/blob/main/LICENSE)。 | 最接近用户描述的“有回应的小伙伴”。可借鉴状态组织方式，但它是 VS Code 扩展，需要重新做安卓渲染及输入。README 单独列出了美术作者及来源，角色素材需逐项确认授权。 |
| [Rive Android](https://github.com/rive-app/rive-android) | 安卓运行时、状态机输入、事件和示例工程；运行时 [MIT](https://github.com/rive-app/rive-android/blob/master/LICENSE)。 | 适合更细腻的交互、骨骼和动作过渡，需要制作 `.riv` 动画文件。当前 README 说明制作可交付文件需要 Rive 账号，免费导出带短启动画面，付费方案可移除；运行时开源与制作工具的条件要分别考虑。 |
| [Lottie Android](https://github.com/airbnb/lottie-android) | 在安卓原生播放由 After Effects / Bodymovin 导出的 JSON 动画；[Apache-2.0](https://github.com/airbnb/lottie-android/blob/master/LICENSE)。 | 适合单次浇水、奖励和庆祝短动画。学习数据、成长阶段、触摸响应和动作之间的切换，仍由应用代码管理。 |
| [Godot](https://github.com/godotengine/godot) | 2D / 3D 游戏引擎，支持 Android 和 Web 导出；[MIT](https://github.com/godotengine/godot/blob/master/LICENSE.txt)。 | 如果以后要能走动、拖动布置、种多棵树，或做可以旋转的 3D 林地，可作为候选。相比当前的单场景 2D 互动，需要额外管理引擎集成、资源、生命周期与性能。 |
| [Mini Pets](https://github.com/melvincwng/mini-pets) | Godot / GDScript 的宠物游戏，README 描述喂食、玩耍、清洁、升级和变色，并提供过 Web、Android 版本。 | 可参考“互动—积累—成长”的玩法结构。README 中维护说明和更新日志停留在较早年份，不能据此认定当前仍在维护；本次未确认完整素材授权，未复制代码或素材。 |
| [VPet](https://github.com/LorisYounger/VPet) | 摸头、提起等多种互动及动画，WPF 桌面宠物；README 有单独的动画授权条件。 | 适合参考完整的陪伴反馈，但其 WPF 核心不能直接作为安卓组件。代码许可与动画授权分开；当前原型未复用其动画。 |

如果只保留现在这片小林地，建议先用原生 2D 动画，保持项目简单。如果以后非常看重角色表情、连续动作混合，可以优先试 Rive；如果决定做可自由布置的 3D 小世界，再考虑 Godot。

## 本轮已落地的交互

- 学习统计默认显示环形图，引导线外标注科目和具体时长，不显示百分比；横条与每日趋势保留为可切换视图。
- 林地用实时 Canvas 场景替换整张静态陪伴插画，包含微风、草叶、光点和蝴蝶。
- 点击小树：轻摆回应并浮现小爱心，不消耗水滴。
- 点击浇水：水壶入场、倾斜洒水、水滴落向根部、涟漪、树苗生长及阶段外形变化。
- 一次浇水的资源与成长结果会先保存，播放期间锁定再次浇水，避免连续点击重复消费；刷新不会再次扣水。
- 第五次浇水：完成成长动画后，再提示入藏；种下下一棵时播放萌发动画。
- 页面不可见或场景滚出视野时暂停绘制，重新进入恢复；用户启用减少动态效果时关闭环境摇摆、简化短动作。

树苗使用项目已有的三个手绘透明素材，通过尺寸变化和淡入淡出表现五级成长。这是可交互的二维分层动画原型，尚未做每片叶子的独立骨骼，也不是 3D 模型。Android 端效果、能耗与锁屏恢复仍需正式接入后验证。

## 本轮新增美术

内置 `image_gen.imagegen` 工具生成透明水壶，已保存：

- `design/assets/study_watering_can.png`：原型引用。
- `artwork/source/decorations/decor_study_watering_can.png`：原图存档。

最终生成提示词：

> Use case: illustration-story. Asset type: an isolated transparent game sprite for a woodland study app. Primary request: one small terracotta garden watering can only, seen in a clean side three-quarter view, the long spout points LEFT and slightly upward, round sprinkler head at the far left, arched handle at upper right. Hand-painted delicate watercolor and fine colored-pencil outlines, warm orange terracotta, subtle aged patina, soft natural highlights, cozy children's botanical storybook. Keep the entire can in frame, centered with a modest clean margin; no water and no water droplets, no ground, no shadow patch, no plant, no character, no extra objects, no text, no lettering, no watermark. Genuine transparent background. This sprite will be rotated by code during watering; handle, body, and spout must be clearly defined.
