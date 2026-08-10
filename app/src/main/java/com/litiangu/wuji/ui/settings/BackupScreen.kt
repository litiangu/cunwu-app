package com.litiangu.wuji.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import com.litiangu.wuji.data.settings.BackupFileInfo
import com.litiangu.wuji.data.settings.BackupRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import com.litiangu.wuji.data.db.WujiDatabaseProvider
import com.litiangu.wuji.data.settings.SettingsDataStore
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据备份页（Step 23 v2，按 5.3 完整对齐 + 含图 zip）。
 *
 * 三卡（对齐 stitch_app_settings_page_redesign/5.3）：
 * 1. 备份提醒：开关 + 3/7/14 天阈值 chips + 上次成功备份。
 * 2. 本地数据备份：创建新备份按钮 + 本地备份列表（每个文件 more_vert 菜单：恢复/分享/保存到手机/删除）。
 * 3. 恢复数据：从本地（列表菜单）或外部（SAF 选 zip）还原。
 *
 * 删除确认弹窗对齐 5.3.2（warning + 确认删除 error 色）；覆盖还原额外确认不可恢复。
 * 导航沿用 MainActivity 布尔状态机（showBackup），以全屏 overlay 叠在设置页之上。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val db = remember { WujiDatabaseProvider.get(context) }
    val settings = remember { SettingsDataStore(context) }
    val viewModel = remember { BackupViewModel(db, settings) }

    var isBusy by remember { mutableStateOf(false) }
    // 备份行 more_vert 菜单：锚点坐标（boundsInWindow）+ 当前打开的备份项。
    var backupMenuAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var backupMenuInfo by remember { mutableStateOf<BackupFileInfo?>(null) }

    // 首帧即真实值：直接读预热好的内存缓存 StateFlow，无默认占位、无闪跳。
    val reminderOn by BackupRepository.reminderOn.collectAsStateWithLifecycle()
    val threshold by BackupRepository.threshold.collectAsStateWithLifecycle()
    val lastBackupAt by BackupRepository.lastBackupAt.collectAsStateWithLifecycle()
    val backups by BackupRepository.backups.collectAsStateWithLifecycle()
    val lastBackupText = remember(lastBackupAt) {
        if (lastBackupAt == 0L) "从未备份"
        else {
            val days = ((System.currentTimeMillis() - lastBackupAt) / 86_400_000).toInt()
            if (days <= 0) "今天" else "${days} 天前"
        }
    }

    // 弹窗待定项。
    var pendingRestoreFile by remember { mutableStateOf<BackupFileInfo?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingDeleteFile by remember { mutableStateOf<BackupFileInfo?>(null) }

    fun snack(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    fun backupFile(info: BackupFileInfo): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "wuji_backups/${info.name}")
    }

    // 外部 zip 还原：SAF 选文件 → 覆盖确认。
    val externalRestoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) pendingRestoreUri = uri
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "数据备份",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 装饰图标：与备份主题一致（5.3 右钮为设置齿轮装饰，本应用设置子页统一用主题装饰钮）。
                    Box(
                        modifier = Modifier
                            .padding(end = WujiSpace.sm)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SettingsBackupRestore,
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
                .padding(horizontal = 20.dp)
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // 卡 1：备份提醒（primary 橙 = 通知/提醒语义）
            BackupCard {
                SectionHeader(
                    icon = Icons.Filled.NotificationsActive,
                    title = "备份提醒",
                    desc = "到达阈值时首页会提示；点备份将打开本页并自动开始创建新备份。",
                    iconTint = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                Text(
                    text = "上次成功备份：$lastBackupText",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "开启备份提醒",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = reminderOn,
                        onCheckedChange = { scope.launch { settings.setBackupReminderOn(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )
                }
                Text(
                    text = "未备份提醒阈值",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline,
                    letterSpacing = 0.05.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 7, 14).forEach { d ->
                        FilterChip(
                            selected = threshold == d,
                            onClick = { scope.launch { settings.setBackupReminderThresholdDays(d) } },
                            label = { Text("${d} 天") },
                            leadingIcon = if (threshold == d) {
                                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }

            // 卡 2：本地数据备份（tertiary 灰 = 文件管理/工具语义，主操作「创建新备份」保持 primary 实心橙）
            BackupCard {
                SectionHeader(
                    icon = Icons.Filled.Folder,
                    title = "本地数据备份",
                    desc = "备份数据至手机存储。",
                    iconTint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isBusy = true
                            val r = viewModel.createBackup(context)
                            isBusy = false
                            r.onSuccess {
                                BackupRepository.refreshBackups()
                                snack("已创建备份：${it.name}")
                            }.onFailure { snack("创建失败：${it.message ?: "未知错误"}") }
                        }
                    },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(WujiRadius.cardLg),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                ) {
                    Icon(Icons.Filled.AddCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(WujiSpace.md))
                    Text("创建新备份", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "本地备份列表 (${backups.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.outline,
                    letterSpacing = 0.05.sp,
                )
                Spacer(Modifier.height(8.dp))
                if (backups.isEmpty()) {
                    Text(
                        text = "暂无备份，点击上方「创建新备份」。",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        backups.forEach { info ->
                            BackupRow(
                                info = info,
                                onShowMenu = { anchor ->
                                    backupMenuAnchor = anchor
                                    backupMenuInfo = info
                                },
                            )
                        }
                    }
                }
            }

            // 卡 3：恢复数据（secondary 中性 = 还原/恢复语义，按钮已降权为 outlineVariant 细边框）
            BackupCard {
                SectionHeader(
                    icon = Icons.Filled.SettingsBackupRestore,
                    title = "恢复数据",
                    desc = "选择本地备份文件以恢复您的数据。",
                    iconTint = MaterialTheme.colorScheme.secondary,
                )
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = { externalRestoreLauncher.launch(arrayOf("application/zip")) },
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(WujiRadius.cardLg),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Icon(Icons.Filled.SettingsBackupRestore, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(WujiSpace.md))
                    Text("从本地恢复数据", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (isBusy) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // 备份行 more_vert 菜单 overlay（与首页 ViewModeMenuOverlay 同款：boundsInWindow 定位 + 全屏遮罩 + graphicsLayer 圆角裁剪）
    if (backupMenuInfo != null) {
        val info = backupMenuInfo!!
        BackupMenuOverlay(
            anchor = backupMenuAnchor,
            onRestore = {
                backupMenuInfo = null
                pendingRestoreFile = info
            },
            onShare = {
                backupMenuInfo = null
                val intent = viewModel.shareBackupIntent(context, backupFile(info))
                try {
                    context.startActivity(Intent.createChooser(intent, "分享备份"))
                } catch (_: Exception) {
                    snack("没有可用的分享应用")
                }
            },
            onSave = {
                backupMenuInfo = null
                scope.launch {
                    isBusy = true
                    val r = viewModel.saveToPhone(context, backupFile(info))
                    isBusy = false
                    r.onSuccess { snack("已保存到手机 Downloads/wuji") }
                        .onFailure { snack("保存失败：${it.message ?: "未知错误"}") }
                }
            },
            onDelete = {
                backupMenuInfo = null
                pendingDeleteFile = info
            },
            onDismiss = { backupMenuInfo = null; backupMenuAnchor = null },
        )
    }

    // 覆盖还原确认（列表 or 外部 zip 共用）
    if (pendingRestoreFile != null || pendingRestoreUri != null) {
        AlertDialog(
            onDismissRequest = {
                pendingRestoreFile = null
                pendingRestoreUri = null
            },
            title = { Text("覆盖还原", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "还原将覆盖当前全部数据（物品、分类、预算、回收站、设置），此操作不可恢复。确定继续吗？",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = pendingRestoreFile
                        val uri = pendingRestoreUri
                        pendingRestoreFile = null
                        pendingRestoreUri = null
                        scope.launch {
                            isBusy = true
                            val r = if (file != null) {
                                viewModel.restoreFromFile(backupFile(file), context)
                            } else {
                                viewModel.restoreFromUri(uri!!, context)
                            }
                            isBusy = false
                            r.onSuccess { s ->
                                BackupRepository.refreshBackups()
                                snack(
                                    "已还原 ${s.items} 件物品 / ${s.categories} 个分类" +
                                        (if (s.trash > 0) " / ${s.trash} 条回收站" else "")
                                )
                            }.onFailure { snack("还原失败：${it.message ?: "未知错误"}") }
                        }
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingRestoreFile = null
                    pendingRestoreUri = null
                }) { Text("取消") }
            },
        )
    }

    // 删除备份确认（对齐 5.3.2）
    if (pendingDeleteFile != null) {
        val info = pendingDeleteFile!!
        AlertDialog(
            onDismissRequest = { pendingDeleteFile = null },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("确定删除备份？", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            text = {
                Text(
                    "备份文件删除后将无法找回，请确认是否继续操作。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeleteFile = null
                        scope.launch {
                            val r = viewModel.deleteBackup(backupFile(info))
                            r.onSuccess {
                                BackupRepository.refreshBackups()
                                snack("已删除备份")
                            }.onFailure { snack("删除失败：${it.message ?: "未知错误"}") }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteFile = null }) { Text("取消") }
            },
        )
    }
}

/** 白底卡：圆角 24dp + 极淡阴影 + 缩进分隔边框，对齐 5.3。 */
@Composable
private fun BackupCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(WujiRadius.cardLg), clip = false),
        shape = RoundedCornerShape(WujiRadius.cardLg),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainer),
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(24.dp), content = content)
    }
}

/** 卡头：48dp 圆形图标 + 标题 + 描述。iconTint 控制图标圈颜色，默认 primary。 */
@Composable
private fun ColumnScope.SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(desc, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 备份列表行：图标 + 名称 + 时间 + more_vert 按钮（菜单由 BackupScreen 层 overlay 渲染）。 */
@Composable
private fun BackupRow(
    info: BackupFileInfo,
    onShowMenu: (androidx.compose.ui.geometry.Rect) -> Unit,
) {
    val dateStr = remember(info.lastModified) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date(info.lastModified))
    }
    // 记录 more_vert 按钮的窗口坐标，点击时回传给父级渲染 overlay。
    var btnBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Inventory2,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(info.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(dateStr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(
            onClick = {
                btnBounds?.let { onShowMenu(it) }
            },
            modifier = Modifier.onGloballyPositioned { coords ->
                btnBounds = coords.boundsInWindow()
            },
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
        }
    }
}

/**
 * 备份行 more_vert 菜单 overlay（与首页 ViewModeMenuOverlay 同款实现）：
 * - 全屏透明遮罩，点击关闭。
 * - 菜单本体用 graphicsLayer { shape = RoundedCornerShape(16dp); clip = true } 强制圆角裁剪，
 *   阴影在 layer 内生成后被裁到圆角外，干净无矩形穿透。
 * - 定位：锚点按钮右下角向下 4dp，水平贴右内缩 16dp（与首页一致）。
 */
@Composable
private fun BackupMenuOverlay(
    anchor: androidx.compose.ui.geometry.Rect?,
    onRestore: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val menuTopDp = if (anchor != null) {
        with(density) { (anchor.top + anchor.height).toDp() } + 4.dp
    } else {
        96.dp
    }

    Box(Modifier.fillMaxSize()) {
        // 全屏透明遮罩：点击关闭
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Transparent)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        // 菜单本体：定位到按钮右下角向下 4dp，水平贴右内缩 16dp
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = menuTopDp, end = 16.dp)
                .width(200.dp)
                .graphicsLayer {
                    shape = RoundedCornerShape(WujiRadius.xl)
                    clip = true
                    shadowElevation = 8.dp.toPx()
                    ambientShadowColor = Color.Black.copy(alpha = 0.12f)
                    spotShadowColor = Color.Black.copy(alpha = 0.12f)
                }
                .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.98f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(WujiRadius.xl),
                )
                .padding(8.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                BackupMenuItem(
                    label = "还原此备份",
                    icon = Icons.Filled.SettingsBackupRestore,
                    onClick = onRestore,
                )
                BackupMenuItem(
                    label = "分享文件",
                    icon = Icons.Filled.Share,
                    onClick = onShare,
                )
                BackupMenuItem(
                    label = "保存到手机",
                    icon = Icons.Filled.Download,
                    onClick = onSave,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                BackupMenuItem(
                    label = "删除备份",
                    icon = Icons.Filled.Delete,
                    onClick = onDelete,
                    isError = true,
                )
            }
        }
    }
}

/** 菜单项行：图标 + 文字，12dp 圆角（rounded-xl），error 态红色。 */
@Composable
private fun BackupMenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    isError: Boolean = false,
) {
    val textColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    val iconTint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, fontSize = 14.sp, color = textColor)
        }
    }
}
