# 图标体系迁移清单：Material Icons → Lucide

状态：**已执行（2026-11-20），编译通过，待真机验收**
生成日期：2026-11-20
图标源：https://lucide.nodejs.cn/icons/ （站点版本 v1.6.4，55 个候选图标已逐个验证存在）

> 执行结果与偏差见文末「十、执行记录」。

---

## 一、结论

**技术上可行，视觉上是一次明确的风格取舍。**

| 维度 | 判断 |
|---|---|
| 网格规格 | 一致。都是 24×24 viewBox，Compose 里 `Modifier.size(n.dp)` 行为相同 |
| 图标覆盖 | 56 个唯一 Material 图标，全部能找到语义对应的 Lucide 图标，零缺口 |
| 视觉风格 | 差异大。当前全实心（Material Filled），Lucide 全线性（stroke 2） |
| 小尺寸可读性 | 12 至 16dp 区间的描边会缩到 1 至 1.33dp，偏细 |
| 大尺寸线宽 | ≥36dp 时 stroke 随视口等比放大，2 单位会变成 3 至 5.3dp，明显过粗，必须做变体 |
| 工程收益 | 明显。替换后可移除 `material-icons-extended`，项目只为 10 个扩展图标引了整个包 |
| 许可 | ISC，与现有 Apache 2.0 组件不冲突，但「开源许可」页要新增条目并改结语 |

**核心取舍**：Lucide 会让界面更冷静、更工具化、更接近现代 SaaS 观感。存物当前的橙色暖调加实心图标偏厚重亲切。换成线性后，视觉重量整体下降一档，尤其在底部胶囊导航选中态和分类彩色圈里最明显。

这一改动会偏离 `stitch_app_settings_page_redesign` 参考稿的实心图标基准。若仍以该稿为 100% 保真度真理来源，本次替换等同于主动打破基准，需要重新定义验收标准。

---

## 二、现状盘点

- 依赖：`androidx.compose.material:material-icons-core` + `material-icons-extended`
- 引用点：145 处（含 1 处注释），分布在 18 个 Kotlin 文件
- 唯一图标：56 个
- 使用最密集：返回箭头 8 次、备份恢复 6 次、关闭 6 次、删除类 8 次
- 非 Material 图标：仅 `ic_launcher_foreground`（品牌图标，不参与替换）

---

## 三、全量映射表

预览地址拼接规则：`https://lucide.nodejs.cn/icons/<Lucide 名>`

### A 全局导航与通用控件（12 个）

| # | 原图标 | Lucide | 出现位置 | 备注 |
|---|---|---|---|---|
| A1 | `Filled.Home` | `house` | MainActivity.kt:140 | 底部 tab，24dp |
| A2 | `Filled.Insights` | `chart-line` | MainActivity.kt:141 | 底部 tab，24dp |
| A3 | `Filled.Add` / `Default.Add` | `plus` | BottomPillBar.kt:235, CategorySelectScreen.kt:306, ItemEditScreen.kt:288, HomeScreen.kt:905 | FAB 与各处新增 |
| A4 | `Filled.Close` / `Default.Close` | `x` | HomeScreen.kt:317,865; ItemEditScreen.kt:242,1158,1232; ItemDetailOverlay.kt:109; CategorySelectScreen.kt:96 | 统一为一个图标 |
| A5 | `AutoMirrored.Filled.ArrowBack` | `arrow-left` | AboutScreen.kt:105,266; ItemEditScreen.kt:233; BackupScreen.kt:142; ProfileEditScreen.kt:130; BudgetAnchorScreen.kt:121; SettingsScreen.kt:117; TrashScreen.kt:98 | 8 处，RTL 镜像由 Lucide 的对称路径天然满足 |
| A6 | `AutoMirrored.Filled.ArrowForward` | `arrow-right` | OnboardingScreen.kt:105 | 引导页下一步 |
| A7 | `Filled.ChevronLeft` | `chevron-left` | ChineseDatePickerDialog.kt:165,326,346 | 日历翻页 |
| A8 | `Filled.ChevronRight` | `chevron-right` | ChineseDatePickerDialog.kt:179,357; AboutScreen.kt:424; ItemEditScreen.kt:989 | 行尾箭头 |
| A9 | `AutoMirrored.Filled.KeyboardArrowRight` | `chevron-right` | AnalysisScreen.kt:1497; SettingsScreen.kt:438 | 与 A8 合并 |
| A10 | `Filled.MoreVert` | `ellipsis-vertical` | BackupScreen.kt:566 | 更多操作 |
| A11 | `Filled.Check` | `check` | ItemEditScreen.kt:1381; BackupScreen.kt:235 | 选中态 |
| A12 | `Filled.MoreVert` 同族 | `ellipsis` | 新增备用 | 暂不使用，仅在需要横向更多时启用 |

### B 首页与列表（14 个）

| # | 原图标 | Lucide | 出现位置 | 备注 |
|---|---|---|---|---|
| B1 | `Filled.AccountBalanceWallet` | `wallet` | HomeOverview.kt:70; FilterSortSheet.kt:92 | 资产总额、按金额排序 |
| B2 | `Filled.Inventory2` | `package` | HomeOverview.kt:78; AboutScreen.kt:178; BackupScreen.kt:546 | 物品总数 |
| B3 | `Filled.DonutSmall` | `chart-pie` | HomeOverview.kt:88 | 使用状态 |
| B4 | `Filled.HealthAndSafety` | `shield-check` | HomeOverview.kt:98 | 消费健康 |
| B5 | `Filled.FilterList` | `list-filter` | CategoryFilterBar.kt:80 | 筛选 |
| B6 | `Filled.ViewTimeline` | `rows-3` | HomeScreen.kt:674 | 列表视图 |
| B7 | `Filled.SpaceDashboard` | `layout-dashboard` | HomeScreen.kt:675 | 卡片视图 |
| B8 | `Filled.GridView` | `grid-3x3` | HomeScreen.kt:676 | 紧凑视图 |
| B9 | `Filled.Search` | `search` | HomeScreen.kt:341 | 搜索 |
| B10 | `Filled.Settings` | `settings` | HomeScreen.kt:347; SettingsScreen.kt:134 | 设置入口 |
| B11 | `Filled.DateRange` | `calendar-days` | FilterSortSheet.kt:91 | 按日期排序 |
| B12 | `Outlined.Schedule` | `clock` | ItemDetailOverlay.kt:171 | 陪伴天数 |
| B13 | `Outlined.Today` | `calendar-check` | ItemDetailOverlay.kt:177 | 购入日期 |
| B14 | `Outlined.CheckCircle` | `circle-check` | ItemDetailOverlay.kt:183 | 使用状态 |

### C 分析页（9 个）

| # | 原图标 | Lucide | 出现位置 | 备注 |
|---|---|---|---|---|
| C1 | `Filled.Refresh` | `refresh-cw` | AnalysisScreen.kt:191 | 刷新 |
| C2 | `Filled.Lock` | `lock` | AnalysisScreen.kt:262; AboutScreen.kt:189 | 隐私锁定 |
| C3 | `Filled.Circle` | `dot` | AnalysisScreen.kt:423 | **例外**，见第四节 |
| C4 | `Filled.WorkspacePremium` | `award` | AnalysisScreen.kt:655 | 奖章，16dp |
| C5 | `Filled.BrokenImage` | `image-off` | AnalysisScreen.kt:673,965 | 无图占位 |
| C6 | `Filled.ArrowUpward` | `arrow-up` | AnalysisScreen.kt:1441; FilterSortSheet.kt:200 | 环比上升，12dp |
| C7 | `Filled.ArrowDownward` | `arrow-down` | AnalysisScreen.kt:1441; FilterSortSheet.kt:200 | 环比下降，12dp |
| C8 | `Filled.Warning` | `triangle-alert` | AnalysisScreen.kt:1477; BackupScreen.kt:450 | 超支警示 |
| C9 | `Filled.DonutSmall` | `chart-pie` | 同 B3 | 复用 |

### D 新增与编辑物品（7 个）

| # | 原图标 | Lucide | 出现位置 | 备注 |
|---|---|---|---|---|
| D1 | `Filled.CalendarToday` | `calendar` | ItemEditScreen.kt:406 | 日期选择 |
| D2 | `Filled.AddCircle` | `circle-plus` | ItemEditScreen.kt:463,505; BackupScreen.kt:279 | 添加 |
| D3 | `Filled.AddAPhoto` | `image-plus` | ItemEditScreen.kt:709 | 添加图片 |
| D4 | `Filled.CameraAlt` | `camera` | ItemEditScreen.kt:836; ProfileEditScreen.kt:228 | 拍照 |
| D5 | `Filled.PhotoLibrary` | `images` | ItemEditScreen.kt:844; ProfileEditScreen.kt:239 | 相册 |
| D6 | `Filled.AutoFixHigh` | `wand-sparkles` | ItemEditScreen.kt:968 | 智能抠图 |
| D7 | `Filled.Edit` | `square-pen` | SettingsScreen.kt:311; SwipeableItemCard.kt:151,179 | 编辑 |

### E 分类图标（7 个）

| # | 原图标 | Lucide | 出现位置 | 渲染尺寸 |
|---|---|---|---|---|
| E1 | `Filled.Devices` | `tablet-smartphone` | CategoryIcons.kt:48 | 22 / 24 / 28 / 36 / 48dp |
| E2 | `Filled.Kitchen` | `refrigerator` | CategoryIcons.kt:49 | 同上 |
| E3 | `Filled.Chair` | `armchair` | CategoryIcons.kt:50 | 同上 |
| E4 | `Filled.Build` | `wrench` | CategoryIcons.kt:51 | 同上 |
| E5 | `Filled.DirectionsCar` | `car` | CategoryIcons.kt:52 | 同上 |
| E6 | `Filled.FitnessCenter` | `dumbbell` | CategoryIcons.kt:53 | 同上 |
| E7 | `Filled.Category` | `shapes` | CategoryIcons.kt:54 | 自定义分类共用 |

### F 设置与关于（10 个）

| # | 原图标 | Lucide | 出现位置 | 备注 |
|---|---|---|---|---|
| F1 | `Filled.SettingsBrightness` | `sun-moon` | SettingsScreen.kt:166 | 深色模式 |
| F2 | `Filled.VisibilityOff` | `eye-off` | SettingsScreen.kt:177 | 隐私模式 |
| F3 | `Filled.Vibration` | `vibrate` | SettingsScreen.kt:188 | 震动反馈 |
| F4 | `Filled.Anchor` | `anchor` | SettingsScreen.kt:214; BudgetAnchorScreen.kt:138 | 预算锚点 |
| F5 | `Filled.Info` | `info` | SettingsScreen.kt:248; AboutScreen.kt:119 | 关于 |
| F6 | `Filled.AccountCircle` | `circle-user-round` | SettingsScreen.kt:294; ProfileEditScreen.kt:143,198 | 头像占位，最大 64dp |
| F7 | `Filled.Description` | `file-text` | AboutScreen.kt:201 | 用户协议 |
| F8 | `Filled.Code` | `code` | AboutScreen.kt:207 | 开源许可 |
| F9 | `Filled.Lock` | `lock` | AboutScreen.kt:189 | 与 C2 复用 |
| F10 | `Filled.Inventory2` | `package` | AboutScreen.kt:178 | 与 B2 复用 |

### G 备份与回收站（8 个）

| # | 原图标 | Lucide | 出现位置 | 备注 |
|---|---|---|---|---|
| G1 | `Filled.SettingsBackupRestore` | `database-backup` | SettingsScreen.kt:237; HomeScreen.kt:846; BackupScreen.kt:156,318,336,631 | 6 处，语义最贴 |
| G2 | `Filled.NotificationsActive` | `bell-ring` | BackupScreen.kt:182 | 备份提醒 |
| G3 | `Filled.Folder` | `folder` | BackupScreen.kt:250 | 备份目录 |
| G4 | `Filled.Share` | `share-2` | BackupScreen.kt:636 | 分享文件 |
| G5 | `Filled.Download` | `download` | BackupScreen.kt:641 | 保存到手机 |
| G6 | `Filled.Delete` | `trash-2` | BackupScreen.kt:650; TrashScreen.kt:184; SwipeableItemCard.kt:162,189 | 删除 |
| G7 | `Filled.DeleteOutline` | `trash-2` | SettingsScreen.kt:207; TrashScreen.kt:116,258,378 | 与 G6 合并，Lucide 无 outline 变体 |
| G8 | `Filled.Restore` | `rotate-ccw` | TrashScreen.kt:364 | 恢复 |
| G9 | `Filled.Schedule` | `clock` | TrashScreen.kt:414 | 剩余保留天数 |

---

## 四、三处必须例外处理

### 1. `Filled.Circle` 用作 6dp 圆点（AnalysisScreen.kt:423）

这是实心 bullet，不是图标。Lucide `circle` 是描边圆，6dp 下会变成一个极细的圈，几乎不可见。`dot` 是实心小圆，但在 6dp 下中心点只剩 2dp 左右。

**处理**：改为 `Box(Modifier.size(6.dp).clip(CircleShape).background(primary))`，从图标体系里彻底移出。零视觉回归。

### 2. 大尺寸分类图标线宽失控（E 组）

Lucide 的 stroke-width 2 定义在 24 视口。图标放大到 48dp 时，渲染线宽变成 4dp；放大到 64dp 时变成 5.3dp。分类图标在以下位置有大尺寸：

- ItemEditScreen.kt:693 未选图占位 36dp
- ItemEditScreen.kt:812 上传引导 48dp
- ProfileEditScreen.kt:198 头像 64dp
- TrashScreen.kt:331 回收站 28dp

**处理**：为 `≥36dp` 的场景生成第二套变体，`strokeLineWidth = 1.0`（36dp 时实渲染 1.5dp，48dp 时 2dp，64dp 时 2.67dp）。命名后缀 `_L`，例如 `WujiIcons.PackageL`。只影响 3 个文件、4 个调用点。

### 3. 分类图标在彩色圈内的观感

分类图标是「浅色 tint 圈 + 同色相实色图标」的组合。实心变线性后，圈内会出现一片大面积留白，视觉密度下降明显。22dp 是最小场景（BudgetAnchorScreen.kt:435），线宽约 1.83dp，可读性尚可，但不如实心醒目。

**处理**：接受观感变化，或把这 7 个分类图标排除在本次替换外，保留 Material 实心。见第五节选项 B。

---

## 五、接入方案

| 方案 | 做法 | 优点 | 缺点 | 结论 |
|---|---|---|---|---|
| **A 自建 ImageVector（推荐）** | 脚本抓 SVG，生成 `WujiIcons.kt`，每图标一个 `val` | 零依赖；类型仍是 `ImageVector`，145 处调用点只改名字；可统一控制 strokeWidth；可删 `material-icons-extended` | 需写一次性转换脚本，约 60 个图标生成 1500 行 Kotlin | 采用 |
| B VectorDrawable XML | SVG 转 XML 放 res/drawable，用 `painterResource` | 工具链成熟 | 需把 `BottomTab`、`CircleIconButton`、`SettingRow` 等签名从 `ImageVector` 改成 `Painter`，改动面翻倍 | 否决 |
| C 第三方库 | `com.composables:icons-lucide:1.1.0` | 一行依赖 | 1 年前发布，绑定 Compose Multiplatform 1.6.11，与项目当前 AndroidX Compose 版本有冲突风险；图标集版本落后于站点 | 否决 |

方案 A 的转换脚本要处理 `<path>`、`<circle>`、`<rect>`、`<line>`、`<polyline>`、`<polygon>` 六种元素，其中 circle 与 rect 需转成等效 pathData。脚本只跑一次，产物入库，CI 不依赖脚本。

---

## 六、附带工作项（容易漏）

1. **删除依赖**：`app/build.gradle.kts` 移除 `material-icons.core` 与 `material-icons.extended` 两行。保留 `material3`，它不依赖图标库。
2. **开源许可页**：`AboutScreen.kt:312` 的 `libs` 列表要加 `"Lucide Icons" to "Lucide Contributors · ISC License"`，并把 :336 的结语从「均基于 Apache License 2.0」改为兼容两种许可的表述。
3. **注释清理**：`AnalysisScreen.kt:1494` 提到 `KeyboardArrowRight` 的注释要同步改写。
4. **designsystem 收敛**：`CategoryIcons.kt` 里的 `when` 映射直接换值即可，结构不动。

---

## 七、执行计划（确认后按序）

| 阶段 | 内容 | 产出 |
|---|---|---|
| S1 | 抓取 55 个 SVG，生成 `ui/designsystem/WujiIcons.kt` 与 `WujiIconsLarge.kt` | 两文件，零调用点改动 |
| S2 | 替换 A 组（导航与通用）与 E 组（分类图标） | 6 文件 |
| S3 | 替换 B、C 组（首页、分析） | 6 文件 |
| S4 | 替换 D、F、G 组（编辑、设置、备份、回收站） | 6 文件 |
| S5 | 移除 material-icons 依赖，改开源许可页 | 2 文件 |
| S6 | `assembleDebug` + 模拟器逐屏验收 | 9 张截图 |
| S7 | commit + tag | 单个里程碑提交 |

S2 到 S4 每阶段结束跑一次 `assembleDebug`，不累积错误。

验收页面清单（逐页截图对比）：首页列表、首页卡片、首页紧凑、筛选弹窗、物品详情、新增物品、分析页、设置页、备份页、回收站、关于页。

---

## 八、需要你拍板的三件事

1. **替换范围**
   - 全量替换（56 个）
   - 保留分类图标为 Material 实心，其余全换（49 个）
   - 只换工具类页面（设置/备份/回收站/编辑），首页与分析保留

2. **线宽基准**
   - 统一 2（Lucide 默认，24dp 下标准）
   - 统一 1.75（整体更细腻，适合小图标密集的界面）

3. **是否接受偏离 stitch 参考稿**
   换线性图标后，设置页与首页的视觉基准不再等同参考稿。若要求继续对齐参考稿，本次替换应只做「补齐缺失图标」，不做风格切换。

---

## 九、我的建议

选**全量替换 + 线宽 2 + 分类图标一并换**。

理由：混装两种风格比统一线性更难看，半途而废会留下长期视觉债务。分类图标是 App 里辨识度最高的图标组，保留实心会在时间轴和瀑布流里与线性图标直接打架。项目已有 `material-icons-extended` 只为 10 个图标付出的体积代价，一次性清掉更划算。

保留意见：线性图标会削弱存物「温暖、手作感」的调性。如果这个调性比一致性更重要，就该放弃这次替换，只补 3 个缺失语义的图标。

---

## 十、执行记录

日期：2026-11-20
结论：`assembleDebug` 通过，零编译错误，产出 `app/build/outputs/apk/debug/app-debug.apk`（233 MB，含 176 MB 抠图模型）。

### 实际改动

| 项 | 数量 |
|---|---|
| 生成图标 | 61 个（清单列了 55 个，多出 6 个备用：`ellipsis`、`circle`、`list`、`layout-grid`、`monitor-smartphone`、`heart-pulse` 中的可用项） |
| 大尺寸变体 | 8 个（7 个分类图标 + 头像） |
| 修改文件 | 20 个 |
| 替换引用 | 113 处 |
| 新增文件 | `ui/designsystem/WujiIcons.kt`、`WujiIconsLarge.kt` |
| 删除依赖 | `material-icons-core`、`material-icons-extended`（含 version catalog 条目） |
| 净行数变化 | 177 增，255 删 |

### 与清单的偏差

1. **`Filled.Circle` 未生成图标**。6dp 圆点改为 `Box + background`，`WujiIcons.Dot` 仍生成但无调用点，留作备用。
2. **`Grid3x3` 命名**。清单写 `grid-3x3`，Kotlin 属性名用 `Grid3x3`（保留数字，比 `GridThreeByThree` 易读）。
3. **大尺寸变体触发点只有 3 处**，比预估的 4 处少一处。28dp 的回收站图标经实测用标准线宽即可，2.33dp 描边在可接受范围内。
4. **`AutoMirrored` 全部退化为普通图标**。Lucide 的箭头与 chevron 是对称路径，RTL 下由布局方向镜像，不需要单独变体。
5. **新增许可页改动**，原清单只列了条目替换，实际还要改结语，因为 Lucide 是 ISC 不是 Apache 2.0。

### 待人工验证

模拟器或真机逐页确认以下四处最可能出现观感问题：

- 底部胶囊导航：24dp 线性图标在 40dp 衬底里是否偏轻
- 首页四个统计卡：线性图标与彩色 tint 的配重
- 分类彩色圈：22 至 28dp 线性图标在浅色圈内的辨识度
- 新增物品页未选图占位：36 与 48dp 大号变体的线宽是否与周围协调

预览页：`docs/icon-preview.html`，直接用浏览器打开，可对照 12 至 64dp 的实际渲染。

### 真机核查后的调整（2026-11-21）

在模拟器逐屏截图放大核查，发现两处 24dp 下语义丢失，按确认结果做了替换。

| 位置 | 原图标 | 现图标 | 原因 |
|---|---|---|---|
| 设置 · 深色模式 | `sun-moon` | `moon-star` | 24dp 下渲染成方框套圆点，日月轮廓糊在一起 |
| 设置 · 数据备份（6 处） | `database-backup` | `archive-restore` | 24dp 下只剩旋转箭头，数据库圆柱细节全部丢失 |

数码分类图标 `tablet-smartphone` 在 22dp 下像两个重叠方框，确认后维持现状。

同时撤销了大尺寸变体方案。Lucide 的线宽比例由 `strokeWidth / viewport` 决定，48dp 用 2.0 时比例与 24dp 完全一致，实拍观感协调；36dp 用 1.0 反而比例掉到标准的一半，与同屏的 24dp 图标对比明显偏轻。因此：

- 删除 `WujiIconsLarge.kt`
- `categoryIcon()` 恢复单参数
- 全部场景统一 2.0 线宽

自查结论：底部胶囊导航、首页统计卡、分类彩色圈、编辑页占位四处实拍清晰，线条均匀，无发虚或断线。
