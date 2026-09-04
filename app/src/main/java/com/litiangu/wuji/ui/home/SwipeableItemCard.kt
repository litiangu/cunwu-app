package com.litiangu.wuji.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.ui.designsystem.fireHapticIfEnabled
import com.litiangu.wuji.ui.theme.WujiRadius
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * 左滑操作密度档位。三种视图模式共用同一套「左滑露出编辑/删除」交互，
 * 只调整按钮区宽度与是否显示文字，保证手势语义完全一致。
 */
enum class SwipeActionDensity(
    val actionWidth: Dp,
    val iconSize: Dp,
    val showLabel: Boolean,
    /** true = 两个按钮上下堆叠（窄列，适合网格单格）；false = 左右并排（对齐 3.2 参考设计） */
    val stacked: Boolean,
) {
    /** 默认列表：宽按钮左右并排 + 文字标签，对齐 Screen 3.2 */
    Normal(actionWidth = 112.dp, iconSize = 20.dp, showLabel = true, stacked = false),

    /** 卡片列表 2×2：窄列上下堆叠，纯图标。单格宽约 150dp，露出 64dp 仍保留主要信息 */
    Compact(actionWidth = 64.dp, iconSize = 20.dp, showLabel = false, stacked = true),

    /** 紧凑列表 3×3：最窄列上下堆叠，纯图标。单格宽约 96dp，按钮触达面仍≥44dp 高 */
    Mini(actionWidth = 56.dp, iconSize = 18.dp, showLabel = false, stacked = true),

    /** 陈列馆 3×3（Step 44）：40dp 单列堆叠纯图标，溢出仅 28dp，配合格外 clipToBounds 消除邻卡重叠 */
    Gallery(actionWidth = 40.dp, iconSize = 18.dp, showLabel = false, stacked = true),
}

/**
 * 左滑露出右侧操作区（编辑/删除），对齐 `3.2应用主页-物品卡片-右滑操作交互界面/screen.png`。
 *
 * 手势语义（三种视图模式统一）：
 * - 按住卡片**向左**拖动 → 内容卡向左平移，右侧按钮区随之露出。
 * - 向右拖动不产生任何露出（偏移被钳制在 0），已展开时向右拖即为收起。
 * - 松手吸附：位移超过按钮区一半展开，否则回弹闭合；快速甩动按速度判定。
 * - 互斥：由外层传入 [isOpen] / [onOpenChange]，保证列表内同时只有一张卡展开。
 */
@Composable
fun SwipeableItemCard(
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    density: SwipeActionDensity = SwipeActionDensity.Normal,
    cornerRadius: Dp = WujiRadius.lg,
    content: @Composable () -> Unit,
) {
    val localDensity = LocalDensity.current
    val context = LocalContext.current
    val actionWidth = density.actionWidth
    val actionWidthPx = with(localDensity) { actionWidth.toPx() }
    // 吸附阈值：位移超过按钮区一半即展开
    val thresholdRatio = 0.5f
    // 快速甩动的速度阈值（px/s），超过即忽略位移直接判定方向
    val flingVelocity = 400f

    val scope = rememberCoroutineScope()
    // 内容卡水平偏移。0 = 闭合；-actionWidthPx = 完全展开（卡片已向左移动）
    val offsetX = remember { Animatable(0f) }

    // 外部状态变化（互斥关闭其它卡）同步到动画
    LaunchedEffect(isOpen) {
        offsetX.animateTo(if (isOpen) -actionWidthPx else 0f)
    }

    val draggableState = rememberDraggableState { delta ->
        // delta < 0 表示手指向左拖：offsetX 变负，卡片左移露出右侧按钮。
        // delta > 0 表示向右拖：offsetX 向 0 回归，钳制上限 0 使闭合态右拖无任何位移。
        scope.launch {
            offsetX.snapTo((offsetX.value + delta).coerceIn(-actionWidthPx, 0f))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // 背后右侧操作区。默认列表左右并排（对齐 3.2）；网格模式上下堆叠，
        // 用窄列换取足够高的按钮触达面，避免单格过窄时按钮宽度不足 44dp。
        val editClick: () -> Unit = {
            context.fireHapticIfEnabled()
            scope.launch {
                offsetX.animateTo(0f)
                onOpenChange(false)
                onEdit()
            }
        }
        val deleteClick: () -> Unit = {
            context.fireHapticIfEnabled()
            scope.launch {
                offsetX.animateTo(0f)
                onOpenChange(false)
                onDelete()
            }
        }
        val actionContainer = Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(actionWidth)
            .clip(RoundedCornerShape(topEnd = cornerRadius, bottomEnd = cornerRadius))

        if (density.stacked) {
            Column(modifier = actionContainer) {
                ActionButton(
                    icon = Icons.Filled.Edit,
                    label = "编辑",
                    iconSize = density.iconSize,
                    showLabel = density.showLabel,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = editClick,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    fillHeight = false,
                )
                ActionButton(
                    icon = Icons.Filled.Delete,
                    label = "删除",
                    iconSize = density.iconSize,
                    showLabel = density.showLabel,
                    backgroundColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    onClick = deleteClick,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    fillHeight = false,
                )
            }
        } else {
            Row(
                modifier = actionContainer,
                horizontalArrangement = Arrangement.End,
            ) {
                ActionButton(
                    icon = Icons.Filled.Edit,
                    label = "编辑",
                    iconSize = density.iconSize,
                    showLabel = density.showLabel,
                    backgroundColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = editClick,
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    icon = Icons.Filled.Delete,
                    label = "删除",
                    iconSize = density.iconSize,
                    showLabel = density.showLabel,
                    backgroundColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    onClick = deleteClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 前景内容卡：跟随手势左移
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = draggableState,
                    onDragStopped = { velocity ->
                        scope.launch {
                            val shouldOpen = when {
                                // 快速左甩：展开
                                velocity < -flingVelocity -> true
                                // 快速右甩：闭合
                                velocity > flingVelocity -> false
                                // 慢速：按位移过半判定
                                else -> offsetX.value.absoluteValue > actionWidthPx * thresholdRatio
                            }
                            offsetX.animateTo(if (shouldOpen) -actionWidthPx else 0f)
                            onOpenChange(shouldOpen)
                        }
                    },
                ),
        ) {
            content()
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    iconSize: Dp,
    showLabel: Boolean,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = true,
) {
    Column(
        modifier = modifier
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
            .background(backgroundColor)
            .clickableNoRipple(onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

// 操作按钮不需要涟漪，避免与滑动手势冲突；但保留点击响应。
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    clickable(
        indication = null,
        interactionSource = MutableInteractionSource(),
        onClick = onClick,
    )
