package com.litiangu.wuji.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex

/**
 * 底栏一级页面容器：三个 tab 常驻组合（keep-alive），切换时只做淡入 + 轻微放大。
 *
 * ── 为什么不用 AnimatedContent ────────────────────────────────
 * AnimatedContent 每次切换都会销毁旧页、从零组合新页。首页最重（三种列表视图 + 图片 + 大量卡片），
 * 切回首页的那一帧组合 + 布局要耗时几百毫秒（debug / 模拟器更明显）。Compose 动画按帧时间推进，
 * 这一帧卡完时 180ms 的转场早已「到点」，于是直接跳到终态 —— 表现为「分析 / 设置 → 主页」没有动画，
 * 而切到较轻的分析页、设置页有动画。
 *
 * ── 做法 ──────────────────────────────────────────────────────
 * - 底栏三个页面在首页就绪后一次性组合，此后常驻；不可见的页面**不放置**（不绘制、不接收触摸），
 *   但组合与状态都保留（滚动位置、筛选条件等切回来原样还在）。
 * - 切换：新页面在上层从 0 淡入、0.97× → 1× 轻微放大；旧页面在前 60% 进度内淡出。
 * - 动画开始前先等两帧，让新页面首次放置 / 绘制的那一帧先过去，计时从流畅帧开始，不会被吞掉。
 * - 切换中途再点别的 tab：当前正在进入的页面变为退场页，新目标从头进入，不跳变。
 *
 * @param content 渲染某个 tab；active = 该 tab 当前是否为选中页（可用于暂停 / 恢复页面内副作用）。
 */
@Composable
fun <T> KeepAliveTabHost(
    selected: T,
    tabs: List<T>,
    modifier: Modifier = Modifier,
    content: @Composable (tab: T, active: Boolean) -> Unit,
) {
    var shown by remember { mutableStateOf(selected) }
    var leaving by remember { mutableStateOf<T?>(null) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(selected) {
        if (selected == shown) return@LaunchedEffect
        leaving = shown
        shown = selected
        progress.snapTo(0f)
        // 等新页面的首帧（放置 + 绘制）过去，再开始计时
        withFrameNanos { }
        withFrameNanos { }
        progress.animateTo(1f, tween(TAB_ENTER_MS, easing = TabEnterEasing))
        leaving = null
    }

    Box(modifier) {
        tabs.forEach { tab ->
            key(tab) {
                val isShown = tab == shown
                val isLeaving = tab == leaving
                Box(
                    Modifier
                        .fillMaxSize()
                        .zIndex(if (isShown) 1f else 0f)
                        .placeOnlyIf(isShown || isLeaving)
                        .graphicsLayer {
                            val t = progress.value
                            when {
                                isShown -> {
                                    alpha = t
                                    val s = TAB_ENTER_SCALE + (1f - TAB_ENTER_SCALE) * t
                                    scaleX = s
                                    scaleY = s
                                }
                                isLeaving -> alpha = 1f - (t / TAB_EXIT_FRACTION).coerceIn(0f, 1f)
                                else -> alpha = 0f
                            }
                        }
                        .then(if (isLeaving) Modifier.consumeAllTouches() else Modifier),
                ) {
                    content(tab, isShown)
                }
            }
        }
    }
}

/** 照常测量（保留组合与状态），只在 visible 时放置：不放置 = 不绘制、不参与触摸命中。 */
private fun Modifier.placeOnlyIf(visible: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        if (visible) placeable.place(0, 0)
    }
}

// 新页面进入时长与曲线：Material 3 Emphasized Decelerate，起步快、尾段柔和
private const val TAB_ENTER_MS = 260
private val TabEnterEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
/** 新页面进入时的起始缩放 */
private const val TAB_ENTER_SCALE = 0.97f
/** 旧页面在进入进度的前 60% 内淡出 */
private const val TAB_EXIT_FRACTION = 0.6f
