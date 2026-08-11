package com.litiangu.wuji.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.db.CategoryDao
import com.litiangu.wuji.data.db.ItemDao
import com.litiangu.wuji.data.db.TrashDao
import com.litiangu.wuji.data.db.TrashEntity
import com.litiangu.wuji.data.db.TrashRepository
import com.litiangu.wuji.ui.designsystem.categoryVisual
import com.litiangu.wuji.ui.item.decodeImagePath
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 最近删除（回收站）列表页（Step 22）。
 *
 * - 顶栏「最近删除」+ 返回钮（与设置页/预算锚点页对称，不覆盖 windowInsets）。
 * - 非空时显示「全部清空」按钮 + 回收站物品列表（按删除时间倒序）；空态提示「暂无已删除物品」。
 * - 每行可「恢复」（写回物品表）或「永久删除」（删快照 + 清私有图文件）。
 * - 永久删除与全部清空均弹确认框，强调「不可恢复」。
 *
 * 导航沿用 MainActivity 的布尔状态机（showTrash），以全屏 overlay 叠在设置页之上；
 * 返回时仅关闭本页，设置页原样露出。本步严格不跨步：30 天自动清理 UI / 数据备份留给后续。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit = {},
    itemDao: ItemDao,
    trashDao: TrashDao,
    categoryDao: CategoryDao,
) {
    val viewModel = remember { TrashViewModel(itemDao, trashDao, categoryDao) }
    // 首帧即真实值：直接读预热好的内存缓存 StateFlow，无默认占位、无加载圈（对齐 Step 25 系列修复）。
    val items by TrashRepository.items.collectAsStateWithLifecycle()
    val categories by TrashRepository.categories.collectAsStateWithLifecycle()

    // 确认弹窗状态机：分别处理「永久删除单个」与「全部清空」两种不可逆操作。
    var confirmTarget by remember { mutableStateOf<TrashEntity?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val colorKeyById = remember(categories) { categories.associate { it.id to it.colorKey } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 与设置页/预算锚点页对称：CenterAlignedTopAppBar 默认按 status bar inset 留白，不覆盖 windowInsets。
            CenterAlignedTopAppBar(
                title = {
                    // headline-lg：Manrope 24sp / 600（DESIGN.md typography.headline-lg）
                    Text(
                        text = "最近删除",
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
                    // 装饰性删除圆钮：与设置页「最近删除」行图标(DeleteOutline)一致；
                    // 40dp 灰圆 + 删除图标，纯装饰不参与交互（对齐设置页右侧齿轮装饰钮样式）。
                    Box(
                        modifier = Modifier
                            .padding(end = WujiSpace.sm)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
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
                .padding(horizontal = 20.dp),
        ) {
            if (items.isEmpty()) {
                // 空库：空态提示（首帧即真实，无加载圈）。
                EmptyTrash()
            } else {
                // 非空：列表（「全部清空」移到页面底部固定按钮，参考编辑资料页底部保存按钮）。
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(WujiSpace.sm),
                ) {
                    items(items, key = { it.id }) { entity ->
                        TrashRow(
                            entity = entity,
                            colorKey = colorKeyById[entity.categoryId],
                            onRestore = { viewModel.restore(entity) },
                            onDelete = { confirmTarget = entity },
                        )
                    }
                    // 底部留白，避免最后一行被底部按钮遮挡。
                    item { Spacer(Modifier.height(96.dp)) }
                }
            }
        }

        // 底部固定「全部清空」按钮（参考编辑资料页底部保存按钮样式；不可恢复操作，用 error 色警示）。
        // 仅在有内容时显示，空库不显示（无可清项）。
        if (items.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Button(
                    onClick = { confirmClear = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(WujiRadius.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(WujiSpace.xs))
                    Text(text = "全部清空", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // 永久删除单个确认框
    if (confirmTarget != null) {
        AlertDialog(
            onDismissRequest = { confirmTarget = null },
            title = { Text(text = "永久删除", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    text = "确定永久删除「${confirmTarget!!.name}」吗？此操作不可恢复，图片也会一并清除。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePermanently(confirmTarget!!)
                        confirmTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("永久删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmTarget = null }) { Text("取消") }
            },
        )
    }

    // 全部清空确认框
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(text = "清空回收站", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    text = "确定清空全部已删除物品吗？此操作不可恢复，所有图片也会一并清除。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        confirmClear = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("全部清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

/** 空态：灰图标 + 提示文字，居中显示。 */
@Composable
private fun EmptyTrash() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(WujiSpace.sm))
            Text(
                text = "暂无已删除物品",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 回收站单行：左侧缩略图（无图回退分类图标圈）+ 名称 + 删除日期 + 右侧「恢复 / 永久删除」。
 * 缩略图优先原图，无原图回退抠图 PNG；透明 PNG 用 Fit 避免留白。
 */
@Composable
private fun TrashRow(
    entity: TrashEntity,
    colorKey: String?,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val visual = categoryVisual(colorKey)
    val bitmap = decodeImagePath(entity.imagePath ?: entity.croppedImagePath)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = 1.dp, shape = RoundedCornerShape(WujiRadius.cardLg), clip = false),
        shape = RoundedCornerShape(WujiRadius.cardLg),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WujiSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧缩略图 / 分类图标圈
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = entity.name,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(visual.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = visual.icon,
                            contentDescription = null,
                            tint = visual.foreground,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.width(WujiSpace.md))
            // 名称 + 删除日期
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entity.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Spacer(Modifier.height(WujiSpace.xs))
                Text(
                    text = "删除于 ${formatDeletedDate(entity.deletedAt)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 右侧操作：恢复 / 永久删除（竖向堆叠，避免按钮过窄）
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onRestore) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(WujiSpace.xs))
                    Text(text = "恢复", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(WujiSpace.xs))
                    Text(text = "永久删除", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private val trashDateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

private fun formatDeletedDate(epochMillis: Long): String =
    trashDateFormatter.format(Date(epochMillis))
