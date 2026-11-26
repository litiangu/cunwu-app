package com.litiangu.wuji.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.settings.DarkModePref
import com.litiangu.wuji.domain.DEFAULT_DAILY_BUDGET_ANCHOR
import com.litiangu.wuji.ui.designsystem.cardOutlineStroke
import com.litiangu.wuji.ui.format.formatMoney
import com.litiangu.wuji.ui.designsystem.floatingBarBottomAvoidance
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.ui.designsystem.Haptics
import com.litiangu.wuji.ui.designsystem.WujiIcons
import com.litiangu.wuji.ui.designsystem.WujiSwitch
import com.litiangu.wuji.ui.item.rememberThumbnailHolding
import com.litiangu.wuji.ui.theme.Ink
import com.litiangu.wuji.ui.theme.LocalThemeTransition
import com.litiangu.wuji.ui.theme.PendingDarkMode
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * 设置主页（对齐 `stitch_app_settings_page_redesign/5.0设置页面/screen.png`）。
 *
 * 结构骨架：
 * - TopAppBar：左返回 + 中标题 + 右装饰圆钮（默认走 status bar inset，不贴顶）
 * - 资料区：80dp 头像 + 昵称 + 副标（无卡底）
 * - 4 组卡：偏好 / 管理 / 数据与账户 / 关于
 *   每行 = IconCircle(40dp, 浅灰) + 标题(weight=1) + 右侧控件
 *   行高 72dp，水平 padding 16dp，分隔线缩进(72dp 起)
 *
 * 字体规格严格按 `DESIGN.md` 的 typography：
 *   - 顶栏标题 = headline-lg   (Manrope 24sp / 600)
 *   - 资料区昵称 = headline-md (Manrope 20sp / 600)
 *   - 资料区副标 = body-md     (Manrope 14sp / 400)
 *   - 分组小标题 = label-caps  (Manrope 12sp / 700 / 0.05em)
 *   - 行内标题   = body-lg     (Manrope 16sp / 500)
 *
 * 偏离 5.0 的有意识裁决：
 * 1. 「深色模式」保留三档分段（防退化 Step 11 已落地架构 `SettingsDataStore.darkModeFlow`）。
 * 2. 「预算锚点」改为箭头行，编辑权移交 Step 20 独立子页（已接入 `BudgetAnchorScreen`，经 `SettingsViewModel.saveBudgetAnchor` 落库）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    /** true = 作为底栏一级页面渲染：无返回箭头、标题左对齐、底部为悬浮导航留白。 */
    asTab: Boolean = false,
    onBudgetAnchor: () -> Unit = {},
    onTrash: () -> Unit = {},
    onBackup: () -> Unit = {},
    onAbout: () -> Unit = {},
    onProfileEdit: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = remember { SettingsDataStore(context) }
    val viewModel = remember { SettingsViewModel(settings) }
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()
    val hapticFeedback by viewModel.hapticFeedback.collectAsStateWithLifecycle()
    val budgetAnchor by viewModel.budgetAnchor.collectAsStateWithLifecycle(DEFAULT_DAILY_BUDGET_ANCHOR)
    // Step 25：资料区昵称/头像实时回显。
    // UserProfileRepository 在应用启动期已预热，StateFlow 首帧即为 DataStore 真实值，
    // 无需 loading、无默认闪跳。
    val userNickname by viewModel.userNickname.collectAsStateWithLifecycle()
    val userAvatarPath by viewModel.userAvatarPath.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Material3 TopAppBar 默认按 status bar inset 留出顶部空间；
            // 不显式覆盖 windowInsets，避免画面贴顶与状态栏重叠。
            if (asTab) {
                // 一级页面形态：左对齐标题，无返回键，与首页/分析页同一套顶栏语言。
                TopAppBar(
                    title = {
                        Text(
                            text = "设置",
                            // 一级页面标题与其他三页同档，不再硬编码字号。
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background,
                    ),
                )
            } else CenterAlignedTopAppBar(
                title = {
                    // headline-lg：Manrope 24sp / 600（DESIGN.md typography.headline-lg）
                    Text(
                        text = "设置",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = WujiIcons.ArrowLeft,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
                    // 装饰性 Settings 圆钮：40dp 灰圆 + 齿轮图标，与左侧返回钮左右均衡；纯装饰不参与交互。
                    Box(
                        modifier = Modifier
                            .padding(end = WujiSpace.sm)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = WujiIcons.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(WujiSpace.sm))
            ProfileSection(
                nickname = userNickname,
                avatarPath = userAvatarPath,
                onEdit = onProfileEdit,
            )

            Spacer(Modifier.height(WujiSpace.md))
            SectionLabel("偏好")
            CardGroup {
                SettingRow(
                    icon = WujiIcons.MoonStar,
                    title = "深色模式",
                    trailing = {
                        ThreeChoiceDarkMode(
                            current = darkMode,
                            onSelect = { scope.launch { settings.setDarkMode(it) } },
                        )
                    },
                )
                RowDivider()
                SettingRow(
                    icon = WujiIcons.EyeOff,
                    title = "隐私模式",
                    trailing = {
                        WujiSwitch(
                            checked = privacyMode,
                            onCheckedChange = { viewModel.setPrivacyMode(it) },
                        )
                    },
                )
                RowDivider()
                SettingRow(
                    icon = WujiIcons.Vibrate,
                    title = "震动反馈",
                    trailing = {
                        WujiSwitch(
                            checked = hapticFeedback,
                            onCheckedChange = { v ->
                                viewModel.setHapticFeedback(v)
                                // 开启动作自身给触感确认；关闭后全局静默（不震）。
                                if (v) Haptics.light(context)
                            },
                        )
                    },
                )
            }

            Spacer(Modifier.height(WujiSpace.md))
            SectionLabel("管理")
            CardGroup {
                SettingRow(
                    icon = WujiIcons.Trash2,
                    title = "最近删除",
                    trailing = { Chevron() },
                    onClick = onTrash,
                )
                RowDivider()
                SettingRow(
                    icon = WujiIcons.Anchor,
                    title = "预算锚点",
                    trailing = {
                        // 实时显示当前值（落库后自动更新）；与 Chevron 同行。
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${formatMoney(budgetAnchor)} / 天",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(WujiSpace.xs))
                            Chevron()
                        }
                    },
                    onClick = onBudgetAnchor,
                )
            }

            Spacer(Modifier.height(WujiSpace.md))
            SectionLabel("数据与账户")
            CardGroup {
                SettingRow(
                    icon = WujiIcons.ArchiveRestore,
                    title = "数据备份",
                    trailing = { Chevron() },
                    onClick = onBackup,
                )
            }

            Spacer(Modifier.height(WujiSpace.md))
            SectionLabel("关于")
            CardGroup {
                SettingRow(
                    icon = WujiIcons.Info,
                    title = "关于本应用",
                    trailing = { Chevron() },
                    onClick = onAbout,
                )
            }

            Spacer(Modifier.height(WujiSpace.xl))
            Spacer(Modifier.height(WujiSpace.xl))
            // 作为 tab 时补出悬浮底栏的高度，滚到底不被胶囊压住。
            if (asTab) Spacer(Modifier.height(floatingBarBottomAvoidance()))
        }
    }
}

/** 资料区：80dp 头像（默认图标或用户图圆形裁切）+ 右下橙色编辑角标 + 大字昵称 + 小字副标。 */
@Composable
private fun ProfileSection(
    nickname: String,
    avatarPath: String,
    onEdit: () -> Unit,
) {
    // 后台降采样解码 + 内存缓存；换头像时保留旧图直到新图就绪（见 ui/item/ThumbnailLoader.kt）
    val avatarBitmap = rememberThumbnailHolding(avatarPath.ifBlank { null }, 80.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = WujiSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(80.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (avatarBitmap != null) {
                    // 用户自定义头像：圆形裁切展示
                    Image(
                        bitmap = avatarBitmap,
                        contentDescription = "用户头像",
                        modifier = Modifier.matchParentSize().clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        WujiIcons.CircleUserRound,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }
            // 右下角橙色编辑角标（叠在头像上）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    WujiIcons.SquarePen,
                    contentDescription = "编辑",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.width(WujiSpace.md))
        Column(modifier = Modifier.weight(1f)) {
            // headline-md：Manrope 20sp / 600（DESIGN.md typography.headline-md）
            Text(
                text = nickname.ifBlank { "本地用户" },
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(WujiSpace.xs))
            // body-md：Manrope 14sp / 400（DESIGN.md typography.body-md）
            Text(
                text = "点击编辑昵称",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 分组小标题：label-caps（Manrope 12sp / 700 / 0.05em letterSpacing / 灰）。 */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.05.em,
        // 第三档：分组标签只是导航，读一次就够，弱于行内标题。
        color = Ink.tertiary,
        modifier = Modifier.padding(start = 4.dp, top = WujiSpace.xs, bottom = WujiSpace.xs),
    )
}

/** 分组卡：白底 + 极轻软阴影（1dp / clip=false）+ 24dp 圆角；用 Surface 容器更稳。 */
@Composable
private fun CardGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.dp,
                shape = RoundedCornerShape(WujiRadius.cardLg),
                clip = false,
            ),
        shape = RoundedCornerShape(WujiRadius.cardLg),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = cardOutlineStroke(),
        tonalElevation = 0.dp,
    ) {
        Column(content = content)
    }
}

/** 单行：IconCircle(40dp) + 标题(weight=1) + 右侧控件；可点击行整行 ripple。 */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    trailing: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    val rowBase = Modifier
        .fillMaxWidth()
        .heightIn(min = 72.dp) // 行高 64 → 72dp，5.0 参考的"舒适密度"留白
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Row(
        modifier = rowBase.padding(horizontal = WujiSpace.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(icon)
        Spacer(Modifier.width(WujiSpace.md))
        // body-lg：Manrope 16sp / 500（DESIGN.md typography.body-lg）
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** 行内图标圈：40dp 圆 + surfaceContainer 浅灰底 + onSurfaceVariant 灰图标。 */
@Composable
private fun IconCircle(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** 行间分隔线：从 72dp 起（16dp padding + 40dp icon + 16dp gap）到右边缘。alpha 0.15 极淡。 */
@Composable
private fun RowDivider() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.width(72.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
        )
    }
}

/** 行末箭头：与 IconCircle 等高的灰图标（无卡底）。 */
@Composable
private fun Chevron() {
    Icon(
        WujiIcons.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp),
    )
}

/**
 * 深色模式三档分段控件。功能权威：三档。
 *
 * 选中底色是**一块**在三档之间滑动的胶囊，而不是三块各自显隐的底色：
 * - 位置 / 宽度：弹簧（阻尼 0.78 带一丝回弹、刚度 700），约 130ms 到位、约 270ms 落定。
 *   分段控件距离短，比弹窗（约 400ms）更快更脆，点下去就到，又不显得生硬。
 * - 文字颜色：200ms 渐变，与底色滑动同时进行，避免「底色还没到、字先变白」。
 * - 底色颜色：250ms 渐变。切换浅/深色时主题 primary 会变，渐变掉换色的跳变。
 *
 * 交互时序：点击后底色**立即**开始滑动（本地 pending 态，不等 DataStore 回流），
 * 点下按钮的同一帧写入偏好并启动主题圆形扩散（圆心 = 底色原位置），与底色滑动同时进行。切主题会让整个 App 重组，若与滑动同帧发生会掉帧，
 * 滑块看起来就会「瞬移」（与筛选弹窗「确定」的问题同源）。连点时只提交最后一次。
 * 非点击引起的变化（如首次进入页面 StateFlow 由初值跳到真实值）直接就位，不播滑动。
 */
@Composable
private fun ThreeChoiceDarkMode(current: DarkModePref, onSelect: (DarkModePref) -> Unit) {
    val options = remember {
        listOf(
            DarkModePref.FOLLOW_SYSTEM to "跟随",
            DarkModePref.NO to "浅色",
            DarkModePref.YES to "深色",
        )
    }
    // 本地选中态：点击即更新，驱动滑块；偏好写入在滑动落定后
    var pending by remember { mutableStateOf(current) }
    // 本次变化是否来自用户点击（只有点击才播滑动）
    var fromTap by remember { mutableStateOf(false) }
    LaunchedEffect(current) {
        if (!fromTap) pending = current
    }
    val selectedIndex = options.indexOfFirst { it.first == pending }.coerceAtLeast(0)

    // 三档各自在 Row 内容区中的 x 与宽度（px），由 onPlaced 回填
    val chipX = remember { mutableStateListOf(0f, 0f, 0f) }
    val chipW = remember { mutableStateListOf(0f, 0f, 0f) }
    val indicatorX = remember { Animatable(0f) }
    // 各档的布局坐标，提交时取其在根布局中的中心作为主题圆形扩散的圆心
    val chipCoords = remember { arrayOfNulls<LayoutCoordinates>(3) }
    val indicatorW = remember { Animatable(0f) }

    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestCurrent by rememberUpdatedState(current)
    // 用户点选引起的主题变化才播放全局淡化过渡（见 ui/theme/Theme.kt）
    val themeTransition = LocalThemeTransition.current
    // 已提交过的档位：防止同一次点选重复提交 / 重复触发扩散
    val committed = remember { arrayOfNulls<DarkModePref>(1) }

    /**
     * 底色当前中心点的根布局坐标（px）。
     * 以任一已挂载档位为参照：档位根坐标中心 + (底色中心 x − 该档内容区中心 x)。
     * 底色与各档都在 Row 内容区坐标系（见 drawBehind 注释），y 方向与档位同高同中心。
     */
    fun indicatorCenterInRoot(): Offset? {
        if (indicatorW.value <= 0f) return null
        val ref = chipCoords.indices.firstOrNull { chipCoords[it]?.isAttached == true } ?: return null
        val refCenter = chipCoords[ref]!!.boundsInRoot().center
        val indicatorCenterX = indicatorX.value + indicatorW.value / 2f
        val refCenterX = chipX[ref] + chipW[ref] / 2f
        return refCenter + Offset(indicatorCenterX - refCenterX, 0f)
    }

    // 提交偏好 + 启动圆形扩散。时机 = **点下按钮的同一帧**，与底色滑动同时开始，零等待。
    // 圆心 = 底色**当前所在位置**的中心（点击瞬间，即原档；连点时为滑动中的实际位置）：
    // 旧主题截图里的底色正好处在圆心，扩散第一帧就被挖掉，不会残留「旧底色」；
    // 新主题层的底色从圆心出发滑向目标档，始终在扩散圆之内。
    fun commit(index: Int) {
        val picked = options[index].first
        if (committed[0] == picked) return
        committed[0] = picked
        if (picked != latestCurrent) {
            val origin = indicatorCenterInRoot()
            themeTransition.animateNextChange(origin = origin)
            // 先乐观生效主题（不等 DataStore 落盘回流，省掉 IO 往返），再持久化
            PendingDarkMode.pref = picked
            latestOnSelect(picked)
        }
    }

    val targetX = chipX[selectedIndex]
    val targetW = chipW[selectedIndex]
    LaunchedEffect(selectedIndex, targetX, targetW) {
        if (targetW <= 0f) return@LaunchedEffect // 尚未测量
        if (!fromTap || indicatorW.value <= 0f) {
            indicatorX.snapTo(targetX)
            indicatorW.snapTo(targetW)
            return@LaunchedEffect
        }
        // 偏好已在点击时提交（见 commit），这里只负责底色滑动
        coroutineScope {
            launch { indicatorX.animateTo(targetX, SegmentSpring) }
            launch { indicatorW.animateTo(targetW, SegmentSpring) }
        }
        fromTap = false
    }

    val indicatorColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.primary,
        animationSpec = tween(250),
        label = "segmentIndicator",
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(2.dp)
            // 放在 padding 之后：绘制坐标系 = 内容区，与各档 positionInParent 同一坐标系
            .drawBehind {
                val w = indicatorW.value
                if (w > 0f) {
                    drawRoundRect(
                        color = indicatorColor,
                        topLeft = Offset(indicatorX.value, 0f),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(18.dp.toPx()),
                    )
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, (pref, label) ->
            ChoiceChip3(
                label = label,
                selected = i == selectedIndex,
                onClick = {
                    if (pref != pending) {
                        fromTap = true
                        committed[0] = null
                        pending = pref
                        commit(i)
                    }
                },
                modifier = Modifier.onPlaced { coords ->
                    chipCoords[i] = coords
                    val x = coords.positionInParent().x
                    val w = coords.size.width.toFloat()
                    if (chipX[i] != x) chipX[i] = x
                    if (chipW[i] != w) chipW[i] = w
                },
            )
        }
    }
}

// 分段滑块弹簧。visibilityThreshold 0.5px：亚像素尾巴不计入，落定判定更干脆，偏好提交不被拖延
private val SegmentSpring = spring(
    dampingRatio = 0.78f,
    stiffness = 700f,
    visibilityThreshold = 0.5f,
)

@Composable
private fun ChoiceChip3(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(200),
        label = "segmentText",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            // 底色由父级滑块统一绘制，这里只保留点击（涟漪按 18dp 胶囊裁剪）
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // label-sm：Manrope 12sp / 500（DESIGN.md typography.label-sm）
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}
