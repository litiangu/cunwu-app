package com.litiangu.wuji.ui.designsystem

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 弹簧底部弹窗（v2：去掉背后页面缩放，改为「遮罩渐暗 + 弹窗弹簧 + 内容错峰入场」）。
 *
 * ── 为什么去掉缩放 ────────────────────────────────────────────
 * v1 让整页缩到 0.92 倍，四周露出黑底；浅色主题下状态栏是深色图标，压在黑底上看不清，
 * 观感也偏「iOS 卡片栈」，与本 App 平铺的页面层级不符。v2 页面保持原位，只铺一层渐暗遮罩，
 * 状态栏区域只是被均匀压暗 32%（Material 3 标准 scrim 透明度），深色图标依然清晰。
 *
 * ── 效果 ──────────────────────────────────────────────────────
 * 1. 打开：弹窗弹簧升起（阻尼 0.82 / 刚度 380，约 400ms，轻微回弹）；遮罩 0 → 32% 同步渐暗。
 * 2. 内容错峰：弹窗容器先到，60ms 后内容从下方 24dp 处淡入上浮，有「先托盘、后上菜」的层次感。
 * 3. 拖动：跟手；往上拖按橡皮筋公式衰减（越拖越重，最多约 120dp）。
 * 4. 关闭预告：往下拖过弹窗高度 1/3 时，把手由 32dp 拉长到 56dp 并轻震一下，提示「松手即关闭」；
 *    拖回阈值以内把手缩回。
 * 5. 松手：超过 1/3 或向下甩速 > 1000dp/s → 带着手指速度弹簧落下关闭；否则弹回原位。
 * 6. 遮罩透明度完全由弹窗位移推导，拖动、回弹、关闭时自动同步。
 * 7. 点遮罩 / 系统返回 / 业务内 [SpringSheetScope.dismiss] 都先播完关闭动画，再回调 onDismissRequest。
 *    业务数据提交走 dismiss(afterClosed = { ... })，保证动画期间背后页面不重组。
 *
 * ── 用法 ──────────────────────────────────────────────────────
 * - [SheetBackdropHost] 挂在应用根层（MainActivity），保证弹窗与遮罩盖住悬浮底栏。
 * - 业务页用法同 ModalBottomSheet：`if (show) SpringBottomSheet(onDismissRequest = { show = false }) { ... }`
 */

// 弹窗位移弹簧：约 400ms 落定，轻微回弹
private val SheetSpring: AnimationSpec<Float> = spring(dampingRatio = 0.82f, stiffness = 380f)
// 内容入场弹簧：无回弹、稍柔，跟在容器后面
private val ContentSpring: AnimationSpec<Float> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

private const val SCRIM_MAX_ALPHA = 0.32f
private const val CONTENT_DELAY_MS = 60L
private val ContentRiseDistance = 24.dp

private val SheetTopCorner = 28.dp
private val RubberBandLimit = 120.dp
/** 弹窗底部向屏幕外多铺的底色，盖住弹簧上冲 / 上拖时露出的空隙 */
private val OverdrawPad = 160.dp
private val FlingVelocityThreshold = 1000.dp
private val HandleWidthIdle = 32.dp
private val HandleWidthArmed = 56.dp

/** 根层共享状态：当前弹窗内容 + 打开进度来源。 */
@Stable
class SheetBackdropState {
    internal var progressSource by mutableStateOf<(() -> Float)?>(null)
    internal var sheet by mutableStateOf<(@Composable () -> Unit)?>(null)
    /** 通用根层浮层（见 [RootOverlay]）：渲染在整页（含悬浮底栏）之上、弹簧底部弹窗之下。 */
    internal val overlays = mutableStateListOf<@Composable () -> Unit>()

    /** 0 = 关闭，1 = 完全展开（已夹紧）。只在 draw 阶段读取，不触发重组。 */
    val progress: Float
        get() = (progressSource?.invoke() ?: 0f).coerceIn(0f, 1f)
}

val LocalSheetBackdrop = staticCompositionLocalOf<SheetBackdropState?> { null }

/** 根层承载：页面原样渲染，弹窗打开时其上叠一层随进度渐暗的遮罩，弹窗再叠在最上面。 */
@Composable
fun SheetBackdropHost(
    state: SheetBackdropState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSheetBackdrop provides state) {
        Box(modifier.fillMaxSize()) {
            content()
            // 根层浮层：盖住悬浮底栏，遮罩能把底栏一起压暗
            state.overlays.forEach { entry -> key(entry) { entry() } }
            val sheet = state.sheet
            if (sheet != null) {
                // 遮罩：drawBehind 内读进度，拖动时只重绘不重组
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val p = state.progress
                            if (p > 0f) drawRect(Color.Black, alpha = SCRIM_MAX_ALPHA * p)
                        },
                )
                sheet()
            }
        }
    }
}

/**
 * 把一段模态浮层（自带遮罩，如物品详情）提升到应用根层渲染，盖在悬浮底栏之上。
 *
 * 为什么需要：悬浮底栏在 WujiApp 根层、叠在各页面**之上**。页面内部渲染的浮层，
 * 遮罩只能压暗页面本身，压不到底栏 —— 底栏保持原亮度浮在暗下去的页面上；
 * 浅色主题下胶囊是浅灰半透明底，在压暗背景上就成了一条发白的实心条，看不出磨砂。
 * 用法：在页面里 `if (show) RootOverlay { MyDialog(...) }`，显示期间组合即可。
 * 不在 [SheetBackdropHost] 之内时原地渲染（兜底）。
 */
@Composable
fun RootOverlay(content: @Composable () -> Unit) {
    val host = LocalSheetBackdrop.current
    if (host == null) {
        content()
        return
    }
    val latest by rememberUpdatedState(content)
    DisposableEffect(host) {
        val entry: @Composable () -> Unit = { latest() }
        host.overlays.add(entry)
        onDispose { host.overlays.remove(entry) }
    }
}

/**
 * 弹窗内容作用域：业务按钮（如「确定」）调用 dismiss() 播放关闭动画后再回调 onDismissRequest。
 *
 * afterClosed：关闭动画**播完后**才执行的动作（如提交筛选条件）。
 * 会引起背后页面大面积重组的数据变更务必放这里，不要在 dismiss() 之前同步执行 ——
 * 否则重组/重新布局与关闭动画挤在同一帧，debug 包与模拟器上会连掉多帧，
 * 400ms 的弹簧被吃掉大半，看起来就是弹窗「啪」一下直接消失。
 */
interface SpringSheetScope {
    fun dismiss(afterClosed: () -> Unit = {})
}

/** 弹簧底部弹窗。必须位于 [SheetBackdropHost] 之内。 */
@Composable
fun SpringBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable SpringSheetScope.() -> Unit,
) {
    val host = LocalSheetBackdrop.current
        ?: error("SpringBottomSheet 需要放在 SheetBackdropHost 之内")
    val latestDismiss by rememberUpdatedState(onDismissRequest)
    val latestContent by rememberUpdatedState(content)
    val latestModifier by rememberUpdatedState(modifier)
    val latestColor by rememberUpdatedState(containerColor)

    DisposableEffect(host) {
        val entry: @Composable () -> Unit = {
            SpringSheetLayout(
                host = host,
                modifier = latestModifier,
                containerColor = latestColor,
                onDismissed = { latestDismiss() },
                content = latestContent,
            )
        }
        host.sheet = entry
        onDispose { if (host.sheet === entry) host.sheet = null }
    }
}

/** 位移 / 手势 / 开合逻辑。offset：0 = 完全展开，sheetHeight = 完全收到屏幕外，负值 = 上拖阻尼区。 */
private class SpringSheetController(
    private val scope: CoroutineScope,
    initialOffset: Float,
    private val rubberLimit: Float,
    private val flingThreshold: Float,
    private val onArmedChanged: (Boolean) -> Unit,
) : SpringSheetScope {
    val offset = Animatable(initialOffset)
    var sheetHeight by mutableFloatStateOf(0f)
    var closing by mutableStateOf(false)
        private set
    /** 下拖已越过 1/3 阈值（松手即关闭） */
    var armed by mutableStateOf(false)
        private set
    var onDismissed: () -> Unit = {}

    private var rawDrag = 0f
    private val dismissThreshold get() = sheetHeight / 3f

    val progress: Float
        get() = if (sheetHeight <= 0f) 0f else 1f - offset.value / sheetHeight

    override fun dismiss(afterClosed: () -> Unit) = dismiss(velocity = 0f, afterClosed = afterClosed)

    fun dismiss(velocity: Float, afterClosed: () -> Unit = {}) {
        if (closing) return
        closing = true
        scope.launch {
            val target = if (sheetHeight > 0f) sheetHeight else offset.value
            // 上界 = 完全收起位：一触到屏幕外就结束，不在屏幕外白等回弹尾巴
            offset.updateBounds(upperBound = target)
            offset.animateTo(target, SheetSpring, initialVelocity = velocity)
            // 先提交业务变更，再移除弹窗；同一帧内完成，背后页面只重组一次
            afterClosed()
            onDismissed()
        }
    }

    fun onDragStarted() {
        if (closing) return
        scope.launch { offset.stop() }
        rawDrag = inverseRubber(offset.value)
    }

    fun onDrag(delta: Float) {
        if (closing) return
        rawDrag += delta
        val target = if (rawDrag >= 0f) rawDrag else -rubber(-rawDrag)
        scope.launch { offset.snapTo(target) }
        updateArmed(target > dismissThreshold)
    }

    fun onDragStopped(velocity: Float) {
        if (closing) return
        if (offset.value > dismissThreshold || velocity > flingThreshold) {
            dismiss(velocity)
        } else {
            updateArmed(false)
            scope.launch { offset.animateTo(0f, SheetSpring, initialVelocity = velocity) }
        }
    }

    private fun updateArmed(value: Boolean) {
        if (armed != value) {
            armed = value
            onArmedChanged(value)
        }
    }

    /** iOS 橡皮筋：x 为原始上拖距离，返回实际位移（渐近 rubberLimit）。 */
    private fun rubber(x: Float): Float =
        (1f - 1f / (x * 0.55f / rubberLimit + 1f)) * rubberLimit

    /** rubber 的反函数：从阻尼区位移还原原始拖动量，保证二次抓取不跳。 */
    private fun inverseRubber(y: Float): Float {
        if (y >= 0f) return y
        val a = (-y / rubberLimit).coerceAtMost(0.99f)
        return -(rubberLimit * (1f / (1f - a) - 1f) / 0.55f)
    }
}

@Composable
private fun SpringSheetLayout(
    host: SheetBackdropState,
    modifier: Modifier,
    containerColor: Color,
    onDismissed: () -> Unit,
    content: @Composable SpringSheetScope.() -> Unit,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val overdrawPx = with(density) { OverdrawPad.toPx() }
    val contentRisePx = with(density) { ContentRiseDistance.toPx() }

    val controller = remember {
        SpringSheetController(
            scope = scope,
            initialOffset = screenHeightPx, // 首帧在屏幕外，量到高度后再弹上来
            rubberLimit = with(density) { RubberBandLimit.toPx() },
            flingThreshold = with(density) { FlingVelocityThreshold.toPx() },
            // 越过关闭阈值时轻震一下（遵循设置页的震动开关）；拖回不震
            onArmedChanged = { armed -> if (armed) context.fireHapticIfEnabled() },
        )
    }
    controller.onDismissed = onDismissed

    // 遮罩透明度由弹窗位移推导 → 拖动、回弹、关闭全程同步
    DisposableEffect(host, controller) {
        host.progressSource = { controller.progress }
        onDispose { host.progressSource = null }
    }

    // 内容入场进度（只在打开时播一次，拖动时不受影响）
    val contentIn = remember { Animatable(0f) }

    val measured = controller.sheetHeight > 0f
    LaunchedEffect(measured) {
        if (measured) {
            controller.offset.snapTo(controller.sheetHeight)
            launch { controller.offset.animateTo(0f, SheetSpring) }
            delay(CONTENT_DELAY_MS)
            contentIn.animateTo(1f, ContentSpring)
        }
    }

    BackHandler { controller.dismiss() }

    val handleWidth by animateDpAsState(
        targetValue = if (controller.armed) HandleWidthArmed else HandleWidthIdle,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "sheetHandle",
    )
    val handleColor = if (controller.armed) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }

    val navInset = bottomBarInsetDp(minGap = 0.dp)
    val dragState = rememberDraggableState { delta -> controller.onDrag(delta) }

    Box(Modifier.fillMaxSize()) {
        // 透明点击层：拦截触摸，点击关闭（压暗由 Host 的遮罩负责）
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { controller.dismiss() },
                ),
        )

        Column(
            modifier
                .align(Alignment.BottomCenter)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .onSizeChanged { controller.sheetHeight = it.height - overdrawPx }
                .graphicsLayer { translationY = controller.offset.value + overdrawPx }
                .clip(RoundedCornerShape(topStart = SheetTopCorner, topEnd = SheetTopCorner))
                .background(containerColor)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStarted = { controller.onDragStarted() },
                    onDragStopped = { velocity -> controller.onDragStopped(velocity) },
                ),
        ) {
            // 拖动把手：越过关闭阈值时拉长加深
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = handleWidth, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(handleColor),
            )
            // 内容：容器到位前从下方 24dp 淡入上浮
            Box(
                Modifier.graphicsLayer {
                    val c = contentIn.value
                    alpha = c
                    translationY = (1f - c) * contentRisePx
                },
            ) {
                Column { content(controller) }
            }
            Spacer(Modifier.height(navInset))
            Spacer(Modifier.height(OverdrawPad))
        }
    }
}
