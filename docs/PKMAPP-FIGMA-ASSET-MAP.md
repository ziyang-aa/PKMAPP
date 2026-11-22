# PKMAPP 视觉稿素材对照表

本文档对应 Figma 原稿中的“PKMAPP · 确认版（当前 HTML）”以及本地 HTML 视觉稿 `D:\pkmAPP\design\pkmapp-visual-redesign.html`。

原则：五个内页使用同一张固定森林背景；首页使用一张可横向平移的完整森林小窝全景图。图片只作为背景或插画图层，不做卡片化裁切，不随页面纵向滚动改变。

## 页面背景

| 页面 | Figma / HTML 位置 | Windows 素材源文件 | 用法 |
| --- | --- | --- | --- |
| 首页 · 森林小窝 | 手机画板最底层 | `D:\pkmAPP\宝可梦账本素材\装饰素材\主页面背景\主页面.png` | 一张完整全景图，填满屏幕；左右滑动时平移同一张图，不切换图片 |
| 明细 | 手机画板最底层 | `D:\pkmAPP\宝可梦账本素材\装饰素材\5个页面的固定背景\背景.png` | 固定铺满，不随纵向内容滚动 |
| 图表 | 手机画板最底层 | `D:\pkmAPP\宝可梦账本素材\装饰素材\5个页面的固定背景\背景.png` | 固定铺满，不随纵向内容滚动 |
| 记账 | 手机画板最底层 | `D:\pkmAPP\宝可梦账本素材\装饰素材\5个页面的固定背景\背景.png` | 固定铺满；计算器和内容在其上方 |
| 攒钱 | 手机画板最底层 | `D:\pkmAPP\宝可梦账本素材\装饰素材\5个页面的固定背景\背景.png` | 固定铺满，不随纵向内容滚动 |
| 我的 | 手机画板最底层 | `D:\pkmAPP\宝可梦账本素材\装饰素材\5个页面的固定背景\背景.png` | 固定铺满；签到弹窗出现时背景作为遮罩下的页面 |

页面外层纸张纹理（仅用于设计板或页面容器的纸张质感）：

`D:\pkmAPP\app\src\main\res\drawable-nodpi\bg_paper_texture.webp`

## 通用页面装饰

| 位置 | Windows / Android 文件 | 用法 |
| --- | --- | --- |
| 明细、图表、记账、攒钱、我的左上角返回按钮 | `D:\pkmAPP\app\src\main\res\drawable-nodpi\return_home_door.webp` | 木门返回图标，保持小尺寸，不要替换成标准线性返回箭头 |
| 首页森林角色 / 空间点缀 | `D:\pkmAPP\app\src\main\res\drawable-nodpi\treecko_waiting.webp` | 作为森林小窝中的角色点缀，避免覆盖主要余额信息 |

## 图表页

收支切换区域横向居中：左边是支出，右边是收入。

| 位置 | 文件 | 用法 |
| --- | --- | --- |
| 支出左侧插画 | `D:\pkmAPP\宝可梦账本素材\草苗龟与储蓄罐插画生成.png` | 草苗龟；放在“支出”文字左侧，尺寸小于文字区域，不遮挡切换按钮 |
| 收入右侧插画 | `D:\pkmAPP\宝可梦账本素材\儿童插画：持放大镜观察金币.png` | 木守宫；放在“收入”文字右侧，和左侧草苗龟视觉重量接近 |
| 支出排行榜 · 餐饮 | `D:\pkmAPP\app\src\main\res\drawable-nodpi\ic_category_expense_dining.webp` | 排行榜第一项 |
| 支出排行榜 · 交通 | `D:\pkmAPP\app\src\main\res\drawable-nodpi\ic_category_expense_transport.webp` | 排行榜第二项 |
| 支出排行榜 · 学习 | `D:\pkmAPP\app\src\main\res\drawable-nodpi\ic_category_expense_study.webp` | 排行榜第三项 |

## 记账页分类图标

分类网格继续使用已经转换好的 WebP，不新增 SVG 或 Kotlin / Compose 图标。

目录：`D:\pkmAPP\app\src\main\res\drawable-nodpi\`

支出分类：

`ic_category_expense_dining.webp` 餐饮、`ic_category_expense_shopping.webp` 购物、`ic_category_expense_drinks.webp` 饮料、`ic_category_expense_transport.webp` 交通、`ic_category_expense_daily.webp` 日用、`ic_category_expense_study.webp` 学习、`ic_category_expense_fruit.webp` 水果、`ic_category_expense_entertainment.webp` 娱乐、`ic_category_expense_clothing.webp` 服饰、`ic_category_expense_gift.webp` 礼物、`ic_category_expense_red_packet.webp` 红包、`ic_category_expense_relationship.webp` 人情、`ic_category_expense_medical.webp` 医疗、`ic_category_expense_subscription.webp` 订阅、`ic_category_expense_loan.webp` 借出、`ic_category_expense_repayment.webp` 还款、`ic_category_expense_custom.webp` 自定义。

收入分类：

`ic_category_income_salary.webp` 工资、`ic_category_income_allowance.webp` 零花、`ic_category_income_part_time.webp` 兼职、`ic_category_income_resale.webp` 转卖。

## 攒钱页与我的页

| 页面位置 | 文件 | 用法 |
| --- | --- | --- |
| 攒钱页故事区 / 秋日旅行目标 | `D:\pkmAPP\app\src\main\res\drawable-nodpi\turtwig_carrying_acorn_jar.webp` | 草苗龟抱橡果罐；放在故事区右侧或目标卡左侧 |
| 攒钱页森林小屋目标 | `D:\pkmAPP\app\src\main\res\drawable-nodpi\bulbasaur_sleeping.webp` | 妙蛙种子；作为第二个目标卡角色 |
| 我的页森林身份 Hero | `D:\pkmAPP\app\src\main\res\drawable-nodpi\bulbasaur_sleeping.webp` | 放在 Hero 右侧，作为森林守护员的陪伴角色 |
| 我的页签到弹窗 | 无新增插画 | 使用纸张弹窗、草木绿日期数字和月切换；已签到日期显示绿色数字，不使用打勾图标 |

## 图层顺序建议

1. 页面固定背景或首页全景图。
2. 低透明度纸张洗色层（仅在素材对比度过高时使用）。
3. 页面内容、角色插画和数据卡片。
4. 固定底部导航。
5. 只有签到按钮打开时才显示签到弹窗遮罩和底部弹窗。

## Android 资源映射说明

HTML 中的设计源素材使用相对路径；Android 实际运行时统一从 `D:\pkmAPP\app\src\main\res\drawable-nodpi\` 读取已转换的 WebP。两张图表页专用 PNG 目前保留在宝可梦账本素材目录，后续若要进入 Android 包，应先转换为合适尺寸的 WebP，再复制到 `drawable-nodpi`，不要在 Java / XML 中直接引用 Windows 绝对路径。

