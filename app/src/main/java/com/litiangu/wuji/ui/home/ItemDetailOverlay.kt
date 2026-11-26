package com.litiangu.wuji.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import kotlin.math.abs
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litiangu.wuji.ui.designsystem.WujiIcons
import com.litiangu.wuji.ui.designsystem.categoryVisual
import androidx.compose.foundation.text.TextAutoSize
import com.litiangu.wuji.ui.home.PRIVACY_MASK
import com.litiangu.wuji.ui.format.formatMoney
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.litiangu.wuji.ui.item.rememberThumbnail
import kotlinx.coroutines.launch

/**
 * 物品详情居中模态浮层（带开合动画：遮罩渐暗 + 卡片弹簧浮起；关闭缩小淡出后才回调 onDismiss），对齐 `3.1应用主页-点击物品预览详细页面/screen.png`。
 *
 * 全屏遮罩（点击关闭）+ 中央 24dp 圆角白卡：
 * 右上 分类徽章 + 状态点 + × 关闭；
 * 中央大图位（无图 → 分类图标占位）；名称 + 最终金额大字 + 日均；有附加/收益时下方小字显示购入价与红绿角标；
 * 三项数据横排等分：陪伴天数 / 购入日期 / 状态。
 */
@Composable
fun ItemDetailOverlay(
    item: ItemUiModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    privacyMasked: Boolean = false,
) {
    val visual = categoryVisual(item.categoryKey)
    val density = LocalDensity.current
    val riseOffsetPx = with(density) { DETAIL_RISE.toPx() }

    // ── 开合动画 ──────────────────────────────────────────────
    // 遮罩与卡片各一条进度，全部只在 graphicsLayer / drawBehind 中读取：动画期间零重组。
    // 打开：遮罩 220ms 渐暗；卡片弹簧从 0.9× 放大、自下方 24dp 上浮到位（阻尼 0.82，约 320ms，
    //       末尾一丝回弹），透明度 160ms 先到 —— 「浮起来」而不是「闪出来」。
    // 关闭：卡片 170ms 加速缩到 0.94× 并淡出、下沉 12dp；遮罩 200ms 同步变浅。
    //       动画播完才回调 onDismiss，所以点 ×、点遮罩、系统返回都有退场。
    val scrim = remember { Animatable(0f) }
    val card = remember { Animatable(0f) }
    val cardAlpha = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val latestDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(Unit) {
        launch { scrim.animateTo(1f, tween(220, easing = DetailDecelerate)) }
        launch { cardAlpha.animateTo(1f, tween(160)) }
        card.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 420f))
    }

    val dismiss: () -> Unit = {
        if (!closing) {
            closing = true
            scope.launch {
                launch { scrim.animateTo(0f, tween(200, easing = DetailAccelerate)) }
                launch { cardAlpha.animateTo(0f, tween(170, easing = DetailAccelerate)) }
                card.animateTo(0f, tween(170, easing = DetailAccelerate))
                latestDismiss()
            }
        }
    }
    BackHandler(onBack = dismiss)

    // 图片：后台线程按卡片宽度降采样解码，避免主线程整图解码卡住打开动画。
    // 解码完成后淡入，未完成时先显示分类图标占位（与无图时一致，不会出现空白块）。
    // 与首页列表共用 ThumbnailLoader（含内存缓存）：同一物品第二次打开详情时大图当帧就在
    val detailThumb = rememberThumbnail(item.displayImagePath(), DETAIL_IMAGE_DECODE_WIDTH)
    val detailBitmap = detailThumb.bitmap

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black, alpha = 0.4f * scrim.value) }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // 全屏遮罩不要涟漪
                onClick = dismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .graphicsLayer {
                    val c = card.value
                    if (!closing) {
                        // 打开：0.9× → 1×（弹簧末尾可略超 1，带一丝回弹），自下方 24dp 上浮
                        val s = OPEN_SCALE + (1f - OPEN_SCALE) * c
                        scaleX = s
                        scaleY = s
                        translationY = riseOffsetPx * (1f - c)
                    } else {
                        // 关闭：c 从当前值走到 0，映射为 1× → 0.94×、下沉 12dp
                        val k = 1f - c.coerceIn(0f, 1f)
                        val s = 1f - (1f - CLOSE_SCALE) * k
                        scaleX = s
                        scaleY = s
                        translationY = riseOffsetPx * DETAIL_SINK_RATIO * k
                    }
                    alpha = cardAlpha.value
                }
                .clip(RoundedCornerShape(WujiRadius.xxl))
                .background(MaterialTheme.colorScheme.surface)
                // 吞掉卡片内的点击，避免穿透到遮罩把弹窗关掉；无涟漪
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(WujiSpace.lg),
        ) {
            // 顶行：分类徽章 + 状态点 + ×
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(visual.background)
                        .padding(horizontal = WujiSpace.sm, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = visual.icon,
                            contentDescription = null,
                            tint = visual.foreground,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = categoryName(item.categoryKey),
                            style = MaterialTheme.typography.labelSmall,
                            color = visual.foreground,
                        )
                    }
                }
                Spacer(Modifier.width(WujiSpace.sm))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (item.soldDate != null) Color(0xFF9E9E9E) else visual.foreground
                        ),
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = dismiss,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(WujiIcons.X, contentDescription = "关闭")
                }
            }
            Spacer(Modifier.height(WujiSpace.md))
            // 大图位（有图显示实际图片，无图回退分类图标占位）
            // [修复19] 容器保持 surfaceContainerLow（深色模式=黑底），与页面深色节奏一致，不靠切底色掩盖。
            // 抠图 PNG 的「黑圆」根因是浅色主体圆被模型误判为背景抠成透明洞（透出容器色），
            // 由 SmartCutoutEngine.fillHoles 近圆形大洞判定修复（bbox 填充率 ≥0.72 填回原图色）。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(192.dp)
                    .clip(RoundedCornerShape(WujiRadius.xl))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = detailBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = item.name,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { alpha = detailThumb.alpha },
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        tint = visual.foreground,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
            Spacer(Modifier.height(WujiSpace.md))
            Text(
                text = item.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            // 价格块：最终金额 + /购入价 + 红/绿角标 + 日均
            if (privacyMasked) {
                Text(
                    text = PRIVACY_MASK,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    softWrap = false,
                )
            } else {
                ItemDetailAmount(item)
            }
            Spacer(Modifier.height(WujiSpace.lg))
            // 三项数据：图标 / 标签（陪伴·购入·状态）/ 数值（385天·日期·使用中）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DetailCell(
                    WujiIcons.Clock,
                    "陪伴",
                    "${item.companionshipDays} 天",
                    iconTint = Color(0xFF0088FF),
                )
                DetailCell(
                    WujiIcons.CalendarCheck,
                    "购入",
                    formatDate(item.purchaseDate),
                    iconTint = Color(0xFFC0392B),
                )
                DetailCell(
                    WujiIcons.CircleCheck,
                    "状态",
                    if (item.soldDate != null) "已售出" else "使用中",
                    iconTint = if (item.soldDate != null) Color(0xFF9E9E9E) else Color(0xFF52C41A),
                )
            }
        }
    }
}

@Composable
private fun DetailCell(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}


/**
 * 详情浮层价格块（上下两行结构，避免不同字号 Text bounds 对齐导致的视觉错位）：
 * - 上行：最终金额 + /购入价 + 红/绿斜体角标全部在同一行，按顶部 bounds 对齐（Top）。
 *   /购入价与最终金额同字号（displaySmall），仅颜色淡化。
 * - 下行：仅显示日均（titleMedium），左对齐。
 * - 无附加/收益：不显示 /购入价 与角标，仅显示最终金额与日均。
 * - 角标符号：净值变化 delta = 最终金额 − 购入价 = 附加 − 收益；> 0（净附加）→ 红色斜体 +¥|delta|；< 0（净收益）→ 绿色斜体 −¥|delta|。
 */
@Composable
private fun ItemDetailAmount(item: ItemUiModel) {
    val base = item.purchasePrice
    val extra = item.addPrice      // 附加费用
    val gain = item.soldPrice      // 收益金额
    val finalAmount = base + extra - gain
    val delta = finalAmount - base
    val hasAdjustment = extra > 0 || gain > 0
    val dailyText = "日均 " + formatMoney(dailyDepreciation(item)) + "/天"

    // 行1：最终金额 + 日均（有无附加均一致）
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = formatMoney(finalAmount),
            // 右侧「日均」占固定宽度，主金额按剩余空间自动降字号，避免六位数溢出。
            autoSize = TextAutoSize.StepBased(
                minFontSize = 20.sp,
                maxFontSize = 36.sp,
                stepSize = 1.sp,
            ),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = dailyText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
    // 行2：仅在有附加/收益时显示，左购入价淡化 + 右红/绿角标
    if (hasAdjustment) {
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "/" + formatMoney(base),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                maxLines = 1,
                softWrap = false,
            )
            val absDelta = abs(delta)
            when {
                delta > 0 -> Text(
                    text = "+" + formatMoney(absDelta),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFFE53935),
                    maxLines = 1,
                    softWrap = false,
                )
                delta < 0 -> Text(
                    text = "-" + formatMoney(absDelta),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF52C41A),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private fun dailyDepreciation(item: ItemUiModel): Double =
    if (item.companionshipDays > 0) item.netAsset / item.companionshipDays else 0.0

// colorKey → 分类名（与分类表预置名称一致）
private fun categoryName(colorKey: String?): String = when (colorKey) {
    "digital" -> "数码"
    "appliance" -> "电器"
    "furniture" -> "家具"
    "tool" -> "工具"
    "sport" -> "运动"
    "traffic" -> "交通"
    "custom" -> "自定义"
    else -> "未分类"
}

private val dateFormatter = SimpleDateFormat("yyyy年M月d日", Locale.CHINA)

private fun formatDate(epochMillis: Long): String = dateFormatter.format(Date(epochMillis))

// ── 详情浮层动画参数 ──────────────────────────────────────────
/** 打开时卡片起始缩放 */
private const val OPEN_SCALE = 0.9f
/** 关闭时卡片缩到的比例 */
private const val CLOSE_SCALE = 0.94f
/** 打开时卡片自下方上浮的距离 */
private val DETAIL_RISE = 24.dp
/** 关闭时下沉距离占上浮距离的比例（12dp） */
private const val DETAIL_SINK_RATIO = 0.5f
// Material 标准减速 / 加速：进场减速落定，退场加速离开
private val DetailDecelerate = CubicBezierEasing(0f, 0f, 0.2f, 1f)
private val DetailAccelerate = CubicBezierEasing(0.4f, 0f, 1f, 1f)
/** 详情大图解码宽度：卡片宽约 0.9 屏宽，360dp 足以清晰显示 */
private val DETAIL_IMAGE_DECODE_WIDTH = 360.dp
