# Jiumi UI/UX 复刻可行性分析

来源仓库：https://github.com/ybblab/Jiumi （Kotlin + Compose + Material3，Apache-2.0，minSdk 30 / compileSdk 35）
对照工程：Wuji（minSdk 34 / targetSdk 36，Compose Material3，Manrope 可变字体）
分析日期：2026-11-21

---

## 一、结论速览

| 用户问题 | 事实结论 | Wuji 可复刻性 |
| --- | --- | --- |
| 底部导航 UI/UX 怎么实现 | 悬浮胶囊 + GraphicsLayer 录制背景 + RenderEffect（模糊/折射）+ 双弹簧滑动 pill | 高。minSdk 34 已覆盖 RuntimeShader，全量可用 |
| 深色模式逻辑怎么实现 | ThemeMode 三档 → AppCompatDelegate 全局 night mode → SharedPreferences 持久化 → Compose 读 isSystemInDarkTheme | 中。Wuji 已有三档 DataStore 方案，缺的是窗口层与系统栏图标对齐 |
| 字体是自带还是第三方 | 全部系统默认字体，零字体文件、零字体依赖，靠 5 级字阶 + 字重 + tnum 建层级 | 无需复刻。Wuji 的 Manrope 方案更优，只需补 2 个细节 |

---

## 二、底部导航机制拆解

### 2.1 结构（`ui/Tabs.kt`）

```
Box(fillMaxSize)
├── Box(录制层)  drawWithContent { backdrop.record { drawContent() }; drawLayer(backdrop) }
│    └── content(selected)        // 整页，可滚到屏幕底边
└── GlassTabBar(align = BottomCenter)
     ├── 背景：画布副本（外扩 24dp）+ RenderEffect 模糊/折射 + 色调层 + 光泽层
     ├── 滑动 pill：offset(left) + width(right - left)，双弹簧
     └── Row：3 个 tab（图标 18dp + 文字 10sp）
```

尺寸常量（`Tabs.kt` 末尾）

| 常量 | 值 | 说明 |
| --- | --- | --- |
| BAR_HEIGHT | 48dp | 胶囊高度 |
| BAR_GAP | 12dp | 距屏幕底边 |
| BAR_PADDING | 4dp | 胶囊内边距 |
| TAB_WIDTH | 84dp | 单 tab 宽度，胶囊宽 = 84 × 3 + 8 = 260dp |

胶囊宽度按内容算，不铺满屏幕。这是它视觉轻的关键。

### 2.2 液态玻璃三层实现（`ui/LiquidGlass.kt`）

| 系统版本 | 手段 | 效果 |
| --- | --- | --- |
| Android 13+ (API 33) | RuntimeShader 胶囊折射 + createBlurEffect 链式组合 | 边缘弯折 + 磨砂 |
| Android 12 (API 31) | 仅 BlurEffect（5dp，radius ×3） | 磨砂 |
| Android 11 及以下 | 无 RenderEffect，纯色 alpha 0.94 | 近似不透明 |

折射 shader 的思路（`LiquidGlass.kt:22`）：用胶囊 SDF 求梯度法线，在距边缘 `band`（16dp）范围内按 `k = strength × (1 - sqrt(1 - t²))` 反向采样。这是圆角倒角的斜率曲线，越靠边偏移越大。刻意不做 RGB 通道分离，注释里写明会引入青边，真实薄玻璃没有色散。

视觉可信度靠三组纯绘制叠层，与 shader 无关，成本为零：

| 层 | 参数（深色 / 浅色） |
| --- | --- |
| 色调 tint | `#1C1C1F` 46% / `#F5F5F8` 50% |
| 光泽 sheen（垂直渐变） | 0% 白 12% / 45% 0 / 80% 0 / 100% 白 7%（浅色 55% / 30%） |
| 边沿 rim（线性渐变 135°） | 0% 白 42% / 35% 6% / 65% 4% / 100% 白 26%（浅色 100% / 25% / 20% / 85%） |
| 外描边 | 0.5dp 黑 55% / 8%，防止白底失去边界 |
| 阴影 | 双层，14dp + 0dp / 22dp + 2dp |

### 2.3 动效与交互

| 行为 | 参数 | 代码位置 |
| --- | --- | --- |
| pill 滑动（液体拉伸） | 前进：左边缘 trail(0.72, 230)、右边缘 lead(0.82, 700)；后退互换 | `Tabs.kt` animateDpAsState |
| 图标选中弹跳 | 1.16× 用 tween(90ms)，回落 spring(0.45, 500) | `Tabs.kt` Animatable |
| 整条按下缩放 | 0.97×，spring(0.6, 600)，全 bar 共用一个 MutableInteractionSource | `Tabs.kt` sink |
| 颜色过渡 | animateColorAsState(Ink.primary / Ink.secondary) | `Tabs.kt` tabColor |
| 触觉 | 仅切换时触发，HapticFeedbackType.TextHandleMove | `Tabs.kt` haptics |
| 无障碍 | selectableGroup() + selectable(role = Role.Tab)，无 ripple（indication = null） | `Tabs.kt` |
| 内容避让 | `LocalTabBarInset = 48 + 12×2 + navBottom`，页面加进列表 contentPadding | `Tabs.kt` |

双弹簧是性价比最高的一段：8 行代码换来"水 drop 拉伸"的手感。物理上左边缘慢、右边缘快，宽度先撑大再收回。

### 2.4 顶部栏联动（`ui/HidingTopBar.kt`）

`NestedScrollConnection.onPreScroll` 把手指位移优先吃掉，顶栏跟随手指平移；`onPostFling` 按是否过半决定吸附展示或隐藏；隐藏后屏幕顶部 20dp 热区可点回。滚动位移与页面位移不打架，靠的是 preScroll 返回 `Offset.Zero`。

---

## 三、深色模式机制拆解

调用链

```
AppPreferences.setThemeMode(mode)
  ├── prefs.putString("theme_mode", mode.name)        // 持久化
  ├── _themeMode.value = mode                          // StateFlow 通知 UI
  └── AppCompatDelegate.setDefaultNightMode(mode.nightMode)   // 全局生效，Activity 自动重建
         ↓
JiumiTheme { val isDark = isSystemInDarkTheme() }      // 读到的是已解析结果
         ↓
CompositionLocalProvider(LocalAppIsDark provides isDark)
SideEffect { WindowCompat.getInsetsController(...).isAppearanceLightStatusBars = !isDark }
```

关键点

1. 三档 `SYSTEM / LIGHT / DARK` 映射 `MODE_NIGHT_FOLLOW_SYSTEM / NO / YES`。
2. `applyTheme()` 在 `Application.onCreate` 调一次，保证冷启动就是正确模式。
3. XML 双份窗口背景，`values/themes.xml` 用 `#F0F0F3`，`values-night/themes.xml` 用 `#000000`，消除冷启动白闪。
4. 深色下字重从 500/600 降到 480/580（`Typography.kt`）。深底浅字会视觉膨胀，降 20 个字重做光学补偿。这条细节在中文 App 里极少见。
5. `LocalAppIsDark` 用 `staticCompositionLocalOf`，供玻璃效果、自绘组件读，比层层传 Boolean 干净。
6. 文字选择手柄也做了跟随，`TextSelectionColors(handleColor = onSurface)`。

Wuji 现状对比

| 项 | Jiumi | Wuji | 差距 |
| --- | --- | --- | --- |
| 档位 | 三档 | 三档 `DarkModePref` | 无 |
| 持久化 | SharedPreferences + StateFlow | DataStore + Flow | Wuji 更现代 |
| 生效方式 | AppCompatDelegate 全局 | Compose `darkTheme` 布尔 | Wuji 无需引 AppCompat |
| 冷启动背景 | values-night 覆盖 | values-night 覆盖 | 无 |
| 系统栏图标 | `isAppearanceLightStatusBars = !isDark` | 依赖 `SystemBarStyle.auto` 读系统 uiMode | **有缺陷，见下** |
| 深色字重补偿 | 有（480/580） | 无（Typography 单例） | 可补 |

发现的既有缺陷（`MainActivity.kt:83`）

`SystemBarStyle.auto` 的 `detectDarkMode` 读 `Configuration.uiMode`，而 Wuji 的应用内强制深色不改 uiMode。结果是：系统浅色 + 应用内选深色时，状态栏图标仍是深色，压在深色背景上不可见。Jiumi 的 `SideEffect` 写法正好修这个问题。

---

## 四、字体结论

| 检查项 | 结果 |
| --- | --- |
| `res/font/` | 不存在 |
| `.ttf` / `.otf` 文件 | 0 个（全仓库扫描） |
| 字体依赖（`libs.versions.toml`） | 无，仅 compose / lifecycle / shizuku |
| `Typography.kt` 里的 fontFamily | 未指定，走系统默认 sans-serif |
| 实际字阶 | 20 / 18 / 14 / 12 / 11 sp，共 5 级 |
| 数字处理 | `fontFeatureSettings = "tnum"`，表格与统计数字等宽对齐 |

它的排版层级全部由字号、字重、行高、颜色承担，没有字体家族差异。这是小型工具类 App 的低成本策略。

Wuji 已有 Manrope 可变字体（`Type.kt`，FontVariation 映射 400/500/600/700），起点高于 Jiumi。可借的两个点：

1. `tnum` 等宽数字，用于 Hero 数字、金额列表、统计行。前提是 Manrope 带 tnum 特性，需实测；不带则只能用等宽 fallback 或固定宽度。
2. 深色字重微调，深色下整体降 10 到 20 个字重，抵消视觉膨胀。

---

## 五、可复刻清单（按投入产出排序）

### P0 零风险高收益（单文件，纯绘制与动画）

| # | 项 | 落点 | 成本 | 收益 |
| --- | --- | --- | --- | --- |
| 1 | 双弹簧滑动 pill | `BottomPillBar.kt` 加一层 offset/width 动画 | 0.5h | 手感质变 |
| 2 | 图标选中弹跳 1.16× | 同上，Animatable | 0.3h | 触感反馈 |
| 3 | 整条按压缩放 0.97 | 共享 InteractionSource | 0.3h | 整体性 |
| 4 | rim + sheen 渐变描边 | 在现有 border 位置换成 Brush | 0.5h | 玻璃质感的主要来源，零渲染成本 |
| 5 | `LocalTabBarInset` 内容避让 | 首页 LazyColumn contentPadding 读取 | 0.5h | 内容可从栏下穿过 |
| 6 | selectableGroup + Role.Tab | 替换 clickable | 0.3h | 无障碍 |
| 7 | 系统栏图标随应用内主题 | `Theme.kt` 加 SideEffect + WindowCompat | 0.5h | 修既有 bug |
| 8 | 深色字重补偿 | `Type.kt` 拆 `wujiTypography(isDark)` | 0.5h | 深色可读性 |
| 9 | tnum 等宽数字 | `Type.kt` 加 fontFeatureSettings | 0.2h | 数字对齐 |

### P1 中等成本（需性能验证）

| # | 项 | 落点 | 成本 | 风险 |
| --- | --- | --- | --- | --- |
| 10 | 真实背景模糊（单层 BlurEffect，不做折射） | GraphicsLayer 录制 + RenderEffect | 1d | 每帧双重绘制，瀑布流图片墙有掉帧风险 |
| 11 | 顶部栏滚动隐藏 | 新建 `HidingTopBar.kt`，NestedScrollConnection | 1d | 与现有 overlay 结构耦合，需逐页改造 |
| 12 | tab 文字标签（10sp） | 底栏加 Text | 0.5h | 与 Wuji 现「纯图标 + 圆点」设计冲突，需视觉决策 |

### P2 高成本

| # | 项 | 落点 | 成本 | 风险 |
| --- | --- | --- | --- | --- |
| 13 | RuntimeShader 折射 | 抄 `LiquidGlass.kt` 全量 | 2d | 需 Profile GPU；Wuji 首页图片多，收益与代价需实测 |
| 14 | 逐帧整页录制 | `TabScaffold` 结构改造 | 3d | 与现有 overlay/AnimatedContent 栈冲突，改动面大 |
| 15 | AppCompatDelegate 全局 night mode | 引入 appcompat | 1d | Wuji 现有 DataStore 方案已够用，引入后 Activity 会重建，收益为负 |

### 不建议照搬

| 项 | 原因 |
| --- | --- |
| 胶囊宽度 260dp 固定 | Wuji 有中间 FAB，260dp 放不下 2 tab + FAB，需按 84 × 2 + 48 + 间距重算 |
| 48dp 高度 | Wuji 现 68dp，且有 40dp 图标衬底 + 圆点，压到 48dp 会挤 |
| 无文字 tab | Jiumi 有文字所以能压到 48dp，去掉文字后 48dp 纯图标栏识别成本上升 |
| 纯黑画布 `#000000` | Wuji 有明确「深色禁纯黑」的既有规范，需先确认例外 |
| BlurEffect 5dp ×3 = 15dp | Jiumi 是 48dp 小面积，Wuji 若加宽会放大采样成本 |

---

## 六、许可证

Apache-2.0，允许商用、修改、闭源分发。义务：

1. 保留版权声明与许可证副本。
2. 保留 `NOTICE` 文件内容。
3. 修改过的文件需标注变更。
4. 源文件头部的 `SPDX-FileCopyrightText: 2026 ybblab` 不要删除。

建议做法：参照实现思路自己写，或在派生文件保留原 SPDX 头并在 NOTICE 追加条目。整段复制 `Tabs.kt` 而不带声明会违反条款。

---

## 七、建议执行顺序

单步闭环，每步独立可验收：

| Step | 内容 | 验收 |
| --- | --- | --- |
| A | P0 #7 系统栏图标修复 | 系统浅色 + 应用内深色，状态栏图标为白 |
| B | P0 #8 #9 Typography 深色补偿 + tnum | 深色下正文不发胀，金额列右对齐 |
| C | P0 #1 到 #4 底栏动效与描边 | 录屏对比 Jiumi，手感一致 |
| D | P0 #5 #6 inset 与无障碍 | TalkBack 读出「首页，标签，已选中」 |
| E | P1 #10 模糊背景（仅首页验证帧率） | 滚动掉帧 < 5%，否则回退到纯磨砂 |
| F | P2 #13 shader 折射（可选） | GPU Profile 无长帧 |

E 与 F 需先做性能基线，不达标就停在 D。玻璃效果的边际收益在 D 已经拿到大半，E/F 只补最后一点光学真实感。

---

## 八、执行记录（2026-11-21）

改动仅 3 个文件，未触碰其它页面：

| 文件 | 改动 |
| --- | --- |
| `ui/theme/Theme.kt` | 加入 SideEffect 覆盖系统栏图标；typography 改为按 darkTheme 生成 |
| `ui/theme/Type.kt` | 新增 480/580 字重实例；`wujiTypography(isDark)`；tnum |
| `ui/designsystem/BottomPillBar.kt` | 滑动指示块、弹跳、按压缩放、顶部高光、无障碍语义 |

### Step A 系统栏图标（通过）

`MainActivity.kt:83` 的 `SystemBarStyle.auto` 依据系统 `uiMode` 判定明暗，应用内三档深色不改 uiMode，修复前存在浅色背景 + 白色图标不可见的情况。改为在 `WujiTheme` 内用 `WindowCompat.getInsetsController(...).isAppearanceLightStatusBars = !darkTheme` 覆盖。

三组实测（模拟器 API 36，1080×2400）：

| 场景 | 结果 | 截图 |
| --- | --- | --- |
| 系统浅色 + 应用深色 | 白色图标 / 深色背景，可见 | `stepA_1_sysLight_appDark.png` |
| 系统浅色 + 应用浅色 | 深灰图标 / 浅色背景，可见 | `stepA_2_sysLight_appLight.png` |
| 系统深色 + 应用浅色 | 深灰图标 / 浅色背景，可见 | `stepA_3_sysDark_appLight.png` |

### Step B 字阶（通过）

深色下 Medium/SemiBold 映射到 480/580，浅色保持 500/600。因 Compose 按 `FontWeight` 就近匹配 `Font` 条目，必须为 480/580 单独注册条目，否则 `variationSettings` 仍取 500/600，补偿失效。

浅色首页改前改后 `¥12,434` bounds 均为 `[281,384][477,456]`，宽度 196px 无变化，tnum 未引起布局位移。截图 `stepB_light.png`、`stepB_dark.png`、`stepB_dark_home.png`。

### Step C 底栏动效（通过）

- 滑动指示块：左右缘各一条弹簧，前进时 right 用 `spring(0.82, 700)`、left 用 `spring(0.72, 230)`，后退互换。
- 图标选中 1.16× 弹跳，整条按压缩放 0.97（共享 `MutableInteractionSource`）。
- 圆点改常驻布局 + alpha 过渡，修掉选中切换时图标上下跳 2dp 的旧问题。
- 顶部高光：浅色白 30%、深色白 7%，55% 处衰减到透明。

拉伸实证：把弹簧临时降到 9/3.2 后用 raw 截帧，中间帧指示块从 103px 拉到 355px，确认双弹簧产生液体拉伸。恢复正式参数后重新编译。

截图 `stepC_0_start.png`、`stepC_1_mid.png`、`stepC_2_end.png`、`stepC_dark_sheen.png`。

按压缩放实测：按住不放，胶囊边缘位移 9px（575px 宽的 1.4%，与 0.97 缩放在中心对称下的单边收缩吻合）。

### Step D inset 与无障碍（通过）

inset 机制已存在（`Insets.kt` 的 `floatingBarBottomAvoidance()` = 系统栏 + 68 + 16 + 8）。分析页滚动到底，页脚完整可见，无遮挡（`stepD_analysis_bottom.png`）。

无障碍改造过程中发现：`Modifier.semantics` 和 `Modifier.selectable` 会被拆成两个语义节点，名称与选中态分离。最终用 `clearAndSetSemantics` 置于 `selectable` 之前，一次给出名称、`Role.Tab`、`selected`、`onClick`。实测节点：

| 状态 | content-desc | selected | clickable |
| --- | --- | --- | --- |
| 未选中 | 首页 | false | true |
| 已选中 | 分析 | true | false |

点击切换页面正常（页面标题变为「物品分析」）。

### Step E / F 暂缓（需决策）

模拟器基线本身不健康：滚动 5 次共 99 帧，janky 57.58%，50th 48ms。这个数据无法作为 E 的达标依据，加模糊后的劣化也无法与模拟器开销区分。

另有一处设计前置条件：当前胶囊底色不透明，模糊背景会被完全遮住。要做 E 必须把胶囊改成半透明（Jiumi 用 46%/50% alpha），这是视觉变更，与「深色模式禁浅色」等既有规范同级，需先确认。

### 发现的既有问题（未修，超出本步范围）

`stepD_analysis_bottom.png` 显示分析页滚动后，列表内容顶到状态栏区域，文字与时间重叠。滚动容器未加 `statusBars` padding，属既有 edge-to-edge 缺陷。

---

## 九、底栏重构为 Jiumi 布局（2026-11-21 下午）

按参考图改结构：左侧半透明胶囊装「主页 / 分析 / 设置」三个带文字 tab，右侧独立圆形「+」，中间不再放 FAB。

### 结构

```
Row (左右 16dp 边距, 高 56dp)
├── Box (weight 1f, 胶囊: 半透明 + 模糊底 + 1dp 亮边 + 16dp 阴影)
│    ├── 滑动指示块 (整 tab 宽, 橙色 20%/13%, 双弹簧)
│    └── Row (selectableGroup)
│         ├── TabItem 主页 (图标 22dp + 文字 10sp)
│         ├── TabItem 分析
│         └── TabItem 设置
├── Spacer 10dp
└── Box (56dp 圆形, 品牌橙, 白 30% 边环, 橙色光晕阴影)
```

尺寸集中在文件末尾常量，改宽度改 `BarMarginH`（16dp），改高度改 `BarHeight` / `FabSize`（56dp）。

### 设置升级为一级 tab

- `MainActivity` 新增 `settingsTab`，底栏 items 从 2 项变 3 项。
- `SettingsScreen` 新增 `asTab` 参数：为真时顶栏换左对齐 `TopAppBar`（28sp）、去掉返回箭头与装饰齿轮、底部补 `floatingBarBottomAvoidance()`。
- 首页齿轮按钮改为切 tab 而非压 overlay。
- 返回链：子页 overlay → 设置 tab → 主页 → 退出。实测两级返回正确。

### 半透明与模糊

`MainActivity` 把四个底层块包进 `drawWithContent` 录制的 `GraphicsLayer`，胶囊在 `drawBehind` 里取该图层对应带状区域（外扩 12dp）做 `BlurEffect(10dp)`，再叠半透明底色。底色浅色白 80%、深色近黑 60%；上沿加白 35%/10% 的垂直渐变高光。

无模糊时内容直接穿透胶囊，「分析」「设置」被下方的 sofa 卡片文字压住，可读性不达标（`stepE_2_blur.png` 对比 `stepE_10_final_dark.png`）。

### 性能

模拟器 API 36，滚动 5 次：

| 版本 | 帧数 | Janky | 50th | 90th |
| --- | --- | --- | --- | --- |
| 无模糊（两 tab 旧版） | 99 | 57.58% | 48ms | 69ms |
| 模糊 16dp / 外扩 24dp | 87 | 74.71% | 65ms | 97ms |
| 模糊 10dp / 外扩 12dp | 103 | 69.90% | 57ms | 73ms |

降参数后回收约 5pp，仍比基线差 12pp。模拟器本身 57% janky，数据只能作相对参考，需真机复测。若真机不达标，最小改动是把 `blur` 降到 6dp 或退回纯半透明不模糊。

### 验收截图

`stepE_2_blur.png`（深色含模糊）、`stepE_4_settings_tab.png`（设置 tab 形态）、`stepE_6_settings_bottom.png`（设置页滚动到底不被遮挡）、`stepE_7_fab.png`（加号打开分类选择、底栏隐藏）、`stepE_8_analysis.png`（分析页）、`stepE_9_final_light.png`、`stepE_10_final_dark.png`。

无障碍实测：未选中节点 `selected=false / clickable=true`，选中节点 `selected=true`，三个 tab 名称齐全。

---

## 十、两个问题的修复（2026-11-21 晚）

### 问题一：浅色下胶囊看起来不透明

原因有两层。一是底色用了纯白 80%，白卡片上纯白胶囊直接融进背景；二是胶囊下方压的本身就是白色卡片与浅灰列表，模糊之后仍是白，玻璃没有可对照的暗部。

改法：

| 项 | 改前 | 改后 |
| --- | --- | --- |
| 胶囊底色 | `White` 80% | `#F4F4F7` 62% |
| 深色胶囊 | `#1C1C1E` 60% | `#1C1C1E` 52% |
| 边环 | 白 90% | 白 70% |
| 阴影 | 16dp / 黑 10% | 20dp / 黑 16% |

带一点冷灰的胶囊在白色卡片上能看出边界，62% 透明度让下方列表的文字轮廓隐约透出，配上 20dp 阴影形成悬浮层次。截图 `stepG_light_glass.png`、`stepG_final_light.png`。

### 问题二：深色下切页白闪

根因不在底栏，也不在 `GraphicsLayer` 录制，而是 **windowBackground 跟随系统 uiMode，不跟随应用内主题**。

证据链：

1. `res/values/colors.xml` 的 `window_background` 是 `#FAF9F9`，`values-night` 只把它覆盖成 `#121212`。
2. Wuji 的深色三档只改 DataStore，不改 uiMode。系统浅色 + 应用内深色时，XML 资源仍取 `values/`，窗口底是近白色。
3. 三个页面都用 `Scaffold` 绘制不透明底色，平时盖住窗口底。`AnimatedContent` 的 `fadeIn`/`fadeOut` 让两页同时 alpha < 1，叠加后总 alpha 不足 1，空隙透出窗口底。
4. 把动画放慢到 2000ms 抓帧，过渡中间帧整屏平均亮度从 25 升到 78，连状态栏一起发灰。

排除过程：先禁用 `drawWithContent` 录制，仍复现；再把底栏 `backdrop` 传 null，仍复现；去掉 `scaleIn` 只留 fade，仍复现。三个变量都排除后，才落到窗口底。

修复：根层 `Box` 加 `background(MaterialTheme.colorScheme.background)`，过渡空隙透出的是主题色，不再依赖窗口底。同一实验复测，全程平均亮度 25 到 27，无峰值。

验证数据（慢动画 2000ms，6 帧）：

| 状态 | 帧 1 | 帧 2 | 帧 3 | 帧 4 | 帧 5 | 帧 6 |
| --- | --- | --- | --- | --- | --- | --- |
| 修复前 | 25 | 25 | 35 | 78 | 51 | 29 |
| 修复后 | 25 | 25 | 25 | 25 | 26 | 27 |

遗留同类问题：冷启动时 Compose 尚未绘制，窗口底仍会露一帧近白色。

### 冷启动窗口底（已修）

窗口底由两处共同保证：

1. `MainActivity.applyStartupWindowBackground()` 在 `super.onCreate` 之后、`setContent` 之前同步读一次主题，把 `window.setBackgroundDrawable` 设成对应色。
2. `WujiTheme` 的 `SideEffect` 在 `colorScheme.background` 变化时再同步一次，覆盖运行中切换主题。

同步读取踩了一个坑：最初用 `runBlocking { darkModeFlow.first() }` 读 DataStore，冷启动直接卡死，splash 停留十几秒仍在白屏，日志显示首帧绘制被推迟到 3.5 秒之后。DataStore 的首次初始化不能在主线程阻塞等待。

改为 `StartupTheme` 用 SharedPreferences 存一份镜像：`SettingsDataStore.setDarkMode` 写入时双写，`WujiApp` 每次读到偏好也回写一次。读取是 O(1)，无阻塞。镜像只影响冷启动第一帧，Compose 起来后仍以 DataStore 为准，短暂不一致会自愈。

验证（系统浅色 + 应用内深色，冷启动连续抓帧）：

| 阶段 | 平均亮度 |
| --- | --- |
| 系统 splash 阶段 | 242 到 247 |
| 首帧进入应用 | 26，无中间过渡帧 |

热启动（HOME 后重新打开）四帧为 30、26、26、26，无白帧。

剩余边界：Android 12 起的系统 splash 背景在应用进程启动前就已绘制，只能取主题里的静态 `windowBackground`，无法读取应用内偏好。因此「系统浅色 + 应用内强制深色」这一种组合下，splash 仍是 `#FAF9F9`。系统深色时走 `values-night`，splash 是 `#121212`，视觉连续。要彻底消除需把 splash 固定成深色，代价是浅色用户启动时闪一下深色，这是取舍，未擅自改。


---

## 十一、深色层次修复（2026-11-21 晚）

### 与 Jiumi 的逻辑差异

| 维度 | Jiumi | Wuji（改造前） |
| --- | --- | --- |
| 触发方式 | `AppCompatDelegate.setDefaultNightMode`，改系统 uiMode，Activity 重建 | DataStore 三档 + Compose 布尔，不重建 |
| 作用范围 | 系统资源层，`values-night` 自动生效 | 仅 Compose 层，XML 资源不跟随 |
| 状态存储 | SharedPreferences + StateFlow | DataStore + Flow |
| 冷启动 | AppCompat 自动应用，窗口已是 night 色 | 窗口底跟随系统 uiMode，会露白（已补缓存） |
| 系统栏图标 | `WindowCompat` 显式设 | 已补 |
| 深色字重 | 500/600 降为 480/580 | 已补 |
| 画布 | `#000000` | `#121212` |
| 卡片底色 | `#1C1C1E`，比画布亮 28 | `#0E0D0E`，比画布暗 4 |
| 层级方向 | 越靠前景越亮 | Lowest 最暗，卡片压在最底层 |
| 文字层级 | 三档 Ink，第三档 alpha 0.6 | 两档 onSurface / onSurfaceVariant |
| 分隔线 | `#38383A` | `#49454F` |
| 深色阴影 | 14dp 黑 60%，实际靠底色差与边框 | 1dp，深色下不可见 |

### 根因

Wuji 的卡片统一用 `surfaceContainerLowest`，深色值是 `#0E0D0E`。这个值比背景 `#121212` 还暗 4 级，卡片在深色下变成「凹陷」，且与背景的明度差只有 4/255，肉眼几乎不可分。浅色下 `surfaceContainerLowest` 是 `#FFFFFF`，白卡片在 `#FAF9F9` 上靠 1dp 阴影浮起，所以浅色一直正常。问题只在深色暴露。

### 调整

| Token | 改前 | 改后 |
| --- | --- | --- |
| BackgroundDark / SurfaceDark | `#121212` | `#0A0A0C` |
| SurfaceContainerLowestDark（卡片） | `#0E0D0E` | `#1C1C1F` |
| SurfaceContainerLowDark | `#151319` | `#222226` |
| SurfaceContainerDark | `#1B1A1F` | `#28282C` |
| SurfaceContainerHighDark | `#25282C` | `#323236` |
| SurfaceContainerHighestDark | `#2C2F33` | `#3C3C41` |
| SurfaceVariantDark | `#49454F` | `#3C3C42` |
| OnSurfaceVariantDark | `#CAC4D0` | `#A8A8B0` |
| OutlineVariantDark | `#49454F` | `#3E3E44` |

改后画布与卡片差 18 级，卡片与高层差 6 到 10 级。与 Jiumi 的 28 级相比更克制，在暖橙品牌下调低一档，避免大面积亮卡刺眼。

顺带把 secondary 系的紫调（`#CAC4D0`、`#4A4458` 一类 M3 默认值）收敛成中性灰，减少暖橙与冷紫并置时的视觉噪音。

截图：`stepH_0_before.png`（改前）、`stepH_1_after.png`（首页）、`stepH_2_analysis.png`（分析页）、`stepH_3_settings.png`（设置页）。

### 补充两项（同日）

**卡片描边**。深色 1dp 阴影落在近黑画布上不可见，卡片只有底色差时边缘发虚。新增 `ui/designsystem/CardOutline.kt` 的 `cardOutlineStroke()`：深色 0.5dp `outlineVariant` 55%，浅色 0.5dp 黑 6%。已接到统计卡、时间线列表卡、分析页 CardBox、设置卡片组、分类选择卡。

0.5dp 是有意选的：视觉上是一条发丝线，Android 会向上取整到 1 至 1.5 物理像素，不参与布局尺寸，也不与 24dp 圆角打架。

**文字三档**。`Tokens.kt` 新增 `Ink` 对象，`primary` / `secondary` / `tertiary`，第三档在 `onSurfaceVariant` 上叠 60% alpha。先接在两个明确的次次要位置：时间线「已陪伴 N 天」与设置页分组标签。其余位置保持原样，避免大面积回归。

截图：`stepI_1_home_dark.png`、`stepI_2_settings_dark.png`、`stepI_3_home_light.png`、`stepI_4_analysis_dark.png`。

---

## 十二、字阶统一（2026-11-21 晚）

### 问题

四个一级页面的标题实测为四种规格：

| 页面 | 原实现 | 实际字号 |
| --- | --- | --- |
| 主页 | `displaySmall` + Bold | 36sp |
| 分析 | `headlineMedium` + Bold | 28sp |
| 设置 | 硬编码 `28.sp` + SemiBold | 28sp |
| 添加新物品 | `headlineLarge` | 32sp |

更隐蔽的一层：`wujiTypography` 只填了 15 个槽位里的 8 个，未填的 `headlineLarge/Medium/Small`、`titleSmall`、`bodySmall` 落回 Material3 默认值，字体是系统 Roboto 而非 Manrope。于是同屏出现两种字体家族，字号又各按 M3 规范，割裂感来自这两处叠加。

### Jiumi 的规划

Jiumi 只有五级，且把每个 Material 槽位都明确映射到这五级，不允许出现第六种大小：

| 级别 | 字号 | 字重 | 用途 |
| --- | --- | --- | --- |
| page | 20 | semibold | 一级页标题，全 App 唯一 |
| large | 18 | semibold | 页面主名、弹窗标题 |
| medium | 14 | semibold | 行标题 |
| body | 14 | normal | 正文 |
| small | 12 | normal | 副标题、说明 |
| caption | 11 | medium | 时间、计数、图表轴 |

它的做法是「先定级，再把每个槽位塞进某一级」，而不是让组件各自挑字号。

### Wuji 的方案

Wuji 是大标题语言，不适合压到 20sp，所以保留六级，但同样只允许这六级存在：

| 级别 | 字号 | 字重 | 槽位 | 用途 |
| --- | --- | --- | --- | --- |
| Display | 36 | Bold | displaySmall | 详情页金额 |
| Page | 32 | Bold | headlineLarge | 一级页标题 |
| Sheet | 24 | Normal | headlineSmall / Medium | 表单、二级页、弹窗 |
| Title | 22 | Bold | titleLarge | 卡片标题 |
| Body | 16 / 14 | Normal | bodyLarge / bodyMedium | 正文 |
| Caption | 12 / 11 | Medium | labelMedium / labelSmall | 辅助 |

15 个槽位全部显式声明 `fontFamily = Manrope`，不再有回退到 Roboto 的缝隙。

### 对齐结果

四页标题统一 `headlineLarge`（32sp Bold Manrope），并用 `uiautomator` 实测三页：

| 页面 | 改前 bounds | 改后 bounds |
| --- | --- | --- |
| 主页 | `[43,155]` | `[43,155]` |
| 分析 | `[53,176]` | `[42,155]` |
| 设置 | `[43,155]` | `[43,155]` |

分析页原来自定义顶栏，水平 20dp、垂直 16dp，与 TopAppBar 的定位差 10px / 21px。改成 16dp / 8dp 后三页基线完全重合。

添加页的容器水平内距从 24dp 收到 20dp，标题与卡片左边界一致。

截图：`stepJ_1_home.png`、`stepJ_2_analysis.png`、`stepJ_3_settings.png`、`stepJ_5_add_aligned.png`、`stepJ_6_analysis_aligned.png`。

---

## 附：源文件索引

| 文件 | 作用 |
| --- | --- |
| `code/app/src/main/java/com/ybblab/jiumi/ui/Tabs.kt` | 底栏全部实现 |
| `code/app/src/main/java/com/ybblab/jiumi/ui/LiquidGlass.kt` | 折射 shader |
| `code/app/src/main/java/com/ybblab/jiumi/ui/theme/Theme.kt` | 明暗配色与 LocalAppIsDark |
| `code/app/src/main/java/com/ybblab/jiumi/ui/theme/Typography.kt` | 5 级字阶 + 深色字重 |
| `code/app/src/main/java/com/ybblab/jiumi/ui/theme/Color.kt` | 中性灰阶与状态色 |
| `code/app/src/main/java/com/ybblab/jiumi/AppPreferences.kt` | ThemeMode 与持久化 |
| `code/app/src/main/java/com/ybblab/jiumi/ui/HidingTopBar.kt` | 顶栏滚动隐藏 |
| `code/app/src/main/java/com/ybblab/jiumi/MainActivity.kt` | 导航栈与 TabScaffold 接入 |
| `code/app/build.gradle.kts` | 依赖清单（无字体库） |
