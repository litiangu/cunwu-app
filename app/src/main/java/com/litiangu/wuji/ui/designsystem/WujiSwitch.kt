package com.litiangu.wuji.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * 弹性开关：滑块随切换横向拉伸 1.20 倍，落位后弹簧收回；轨道底色 280ms 渐变到主题橙。
 *
 * 动效参数：
 * - 位移 220ms（FastOutSlowIn），期间 thumb scaleX = 1.20
 * - 落位后 scaleX 用 spring 收回：stiffness = 200，阻尼 c = 14
 *   Compose spring 接收的是阻尼比，故按临界阻尼换算：ζ = c / (2√(k·m)) = 14 / (2√200) ≈ 0.495
 *
 * 尺寸沿用 M3：轨道 52×32dp，thumb 22dp，触摸区 52×48dp（不改变设置行布局）。
 */
@Composable
fun WujiSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(durationMillis = 280),
        label = "wujiSwitchTrack",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.outline,
        animationSpec = tween(durationMillis = 280),
        label = "wujiSwitchThumb",
    )

    // travel：0 = 关，1 = 开。stretch：thumb 横向形变量。
    val travel = remember { Animatable(if (checked) 1f else 0f) }
    val stretch = remember { Animatable(1f) }
    var initialized by remember { mutableStateOf(false) }

    LaunchedEffect(checked) {
        if (!initialized) {
            initialized = true
            return@LaunchedEffect
        }
        // 起步即横向拉伸，滑块在变形状态下走完全程。
        stretch.snapTo(StretchFactor)
        travel.animateTo(
            targetValue = if (checked) 1f else 0f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        )
        // 到位后弹簧收回，欠阻尼带来轻微回弹。
        stretch.animateTo(
            targetValue = 1f,
            animationSpec = spring(stiffness = Stiffness, dampingRatio = DampingRatio),
        )
    }

    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width = TrackWidth, height = TrackTouchHeight)
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(width = TrackWidth, height = TrackHeight)) {
            val trackRadius = size.height / 2f
            drawRoundRect(
                color = trackColor,
                cornerRadius = CornerRadius(trackRadius),
            )

            val thumbRadius = ThumbSize.toPx() / 2f
            val edge = TrackPadding.toPx()
            val startX = edge + thumbRadius
            val endX = size.width - edge - thumbRadius
            val centerX = startX + (endX - startX) * travel.value
            val centerY = size.height / 2f

            scale(scaleX = stretch.value, scaleY = 1f, pivot = Offset(centerX, centerY)) {
                drawCircle(
                    color = thumbColor,
                    radius = thumbRadius,
                    center = Offset(centerX, centerY),
                )
            }
        }
    }
}

private val TrackWidth = 52.dp
private val TrackHeight = 32.dp
private val TrackTouchHeight = 48.dp
private val ThumbSize = 22.dp
private val TrackPadding = 5.dp

private const val StretchFactor = 1.20f
private const val Stiffness = 200f

// 阻尼 c = 14 换算为弹簧阻尼比（质量取 1）。
private val DampingRatio = 14f / (2f * sqrt(Stiffness))
