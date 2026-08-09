package com.litiangu.wuji.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.settings.DarkModePref
import com.litiangu.wuji.domain.DEFAULT_DAILY_BUDGET_ANCHOR
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.ui.designsystem.Haptics
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace
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
    onBudgetAnchor: () -> Unit = {},
    onTrash: () -> Unit = {},
    onBackup: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = remember { SettingsDataStore(context) }
    val viewModel = remember { SettingsViewModel(settings) }
    val darkMode by viewModel.darkMode.collectAsStateWithLifecycle()
    val privacyMode by viewModel.privacyMode.collectAsStateWithLifecycle()
    val hapticFeedback by viewModel.hapticFeedback.collectAsStateWithLifecycle()
    val budgetAnchor by viewModel.budgetAnchor.collectAsStateWithLifecycle(DEFAULT_DAILY_BUDGET_ANCHOR)
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Material3 TopAppBar 默认按 status bar inset 留出顶部空间；
            // 不显式覆盖 windowInsets，避免画面贴顶与状态栏重叠。
            CenterAlignedTopAppBar(
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
            ProfileSection(onEdit = { /* TODO Step 25：进入用户信息编辑子页 */ })

            Spacer(Modifier.height(WujiSpace.md))
            SectionLabel("偏好")
            CardGroup {
                SettingRow(
                    icon = Icons.Filled.SettingsBrightness,
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
                    icon = Icons.Filled.VisibilityOff,
                    title = "隐私模式",
                    trailing = {
                        Switch(
                            checked = privacyMode,
                            onCheckedChange = { viewModel.setPrivacyMode(it) },
                        )
                    },
                )
                RowDivider()
                SettingRow(
                    icon = Icons.Filled.Vibration,
                    title = "震动反馈",
                    trailing = {
                        Switch(
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
                    icon = Icons.Filled.DeleteOutline,
                    title = "最近删除",
                    trailing = { Chevron() },
                    onClick = onTrash,
                )
                RowDivider()
                SettingRow(
                    icon = Icons.Filled.Anchor,
                    title = "预算锚点",
                    trailing = {
                        // 实时显示当前值（落库后自动更新）；与 Chevron 同行。
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "¥${if (budgetAnchor % 1.0 == 0.0) budgetAnchor.toLong().toString() else budgetAnchor.toString()} / 天",
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
                    icon = Icons.Filled.CloudUpload,
                    title = "数据备份",
                    trailing = { Chevron() },
                    onClick = onBackup,
                )
            }

            Spacer(Modifier.height(WujiSpace.md))
            SectionLabel("关于")
            CardGroup {
                SettingRow(
                    icon = Icons.Filled.Info,
                    title = "关于本应用",
                    trailing = { Chevron() },
                    onClick = { /* TODO Step 23：跳转关于页 */ },
                )
            }

            Spacer(Modifier.height(WujiSpace.xl))
            Spacer(Modifier.height(WujiSpace.xl))
        }
    }
}

/** 资料区：80dp 灰圆头像 + 灰色人形 + 右下橙色编辑角标 + 大字昵称 + 小字副标。 */
@Composable
private fun ProfileSection(onEdit: () -> Unit) {
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
                Icon(
                    Icons.Filled.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(56.dp),
                )
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
                    Icons.Filled.Edit,
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
                text = "本地用户",
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(22.dp),
    )
}

/** 深色模式三档分段控件。功能权威：三档。 */
@Composable
private fun ThreeChoiceDarkMode(current: DarkModePref, onSelect: (DarkModePref) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChoiceChip3("跟随", current == DarkModePref.FOLLOW_SYSTEM) { onSelect(DarkModePref.FOLLOW_SYSTEM) }
        ChoiceChip3("浅色", current == DarkModePref.NO) { onSelect(DarkModePref.NO) }
        ChoiceChip3("深色", current == DarkModePref.YES) { onSelect(DarkModePref.YES) }
    }
}

@Composable
private fun ChoiceChip3(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // label-sm：Manrope 12sp / 500（DESIGN.md typography.label-sm）
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}