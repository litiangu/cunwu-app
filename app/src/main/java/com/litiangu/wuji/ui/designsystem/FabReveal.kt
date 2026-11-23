package com.litiangu.wuji.ui.designsystem

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 「+」按钮 ↔ 添加页 的圆弧扩散 / 圆弧收拢（v4）。
 *
 * 圆心始终是「+」按钮中心。打开与关闭**不是**简单倒放，各自按操作直觉设计：
 *
 * ── 打开：圆弧扩散 ────────────────────────────────────────────
 * 起始半径 = 按钮半径，圆弧向外扩，一路扫到离按钮最远的左上角，
 * 「添加新物品」标题被扫出来时页面完整。同时：
 * - 「+」与页面出现同步淡出并缩到 0.6×，页面完整的那一刻恰好消失；
 * - 左侧导航胶囊在圆弧扫过它时淡出（像被页面盖住）；
 * - 页面内容以按钮为中心从 0.94× 轻微舒展到 1×，有「向左上角铺开」的感觉。
 *
 * ── 关闭：圆弧回收（v5）─────────────────────────────────────
 * 与打开严格对称：圆心不动（「+」中心），圆弧从左上角开始，一圈圈同心地向右下角的「+」回收，
 * 圆弧扫过的地方页面消失、露出首页；页面内容以按钮为中心从 1× 轻微收拢到 0.94×，
 * 「+」同步重新出现，最后圆收成按钮大小、与按钮重合。
 *
 * v4 曾让整页按圆弧半径大幅缩放（最后缩到 0.04×），缩小后的页面盖不满圆：
 * 圆内、缩略页之外的区域透出首页，于是出现「两层背景叠在一起」、画面闪烁混乱。v5 去掉了大幅缩放。
 *
 * ── 圆内兜底底色 ──────────────────────────────────────────────
 * 内容有 0.94× 的轻微缩放，圆在横向扫到屏幕左缘时，缩放后的页面左缘会比圆略靠内几 dp，
 * 缝里同样会透出首页。因此圆内先铺一层页面底色（surface），再画缩放后的内容，
 * 圆内永远只有「一层」页面。
 *
 * ── 背后首页模糊 ──────────────────────────────────────────────
 * 过渡期间首页随进度模糊（16dp，前 35% 进度内平滑淡入）并轻压暗，
 * 圆弧里的页面清晰、圆外的首页虚化退后，层级一目了然。关闭时反向，首页逐渐变清晰。
 * 页面完全展开时首页被盖住，模糊关闭以省 GPU。见 [fabRevealBackdropBlur]。
 *
 * ── 与按钮交接 ────────────────────────────────────────────────
 * 进度最后 15%（关闭收尾 / 打开起步），圆内页面整体淡出 / 淡入，
 * 收成按钮大小时只剩「+」本身，不会有一块白色圆片在按钮后面闪一下。
 *
 * ── 时长与曲线 ────────────────────────────────────────────────
 * - 打开 600ms，与关闭同一条曲线 CubicBezier(0.35, 0, 0.25, 1)：起步柔和，看得清圆弧从「+」长出来；
 *   中段稳定地向左上角铺开；结尾减速，标题被扫出时轻轻落定。打开与关闭节奏完全对称。
 * - 关闭 600ms，CubicBezier(0.35, 0, 0.25, 1)：起步柔和，能看清圆弧从左上角开始回收；
 *   中段稳定推进，看得出页面在往右下角的「+」收；结尾减速落进按钮，与「+」平稳交接。
 *   （v4 的 380ms + 前段猛冲曲线，大半页面在前 100ms 就被扫掉，看不出方向。）
 */

private const val REVEAL_OPEN_MS = 600
private const val REVEAL_CLOSE_MS = 600
private val RevealOpenEasing = CubicBezierEasing(0.35f, 0f, 0.25f, 1f)
private val RevealCloseEasing = CubicBezierEasing(0.35f, 0f, 0.25f, 1f)
/** 圆内页面在进度最后（关闭）/ 最初（打开）这段整体淡出 / 淡入，与按钮干净交接。 */
private const val PAGE_FADE_PHASE = 0.15f
/** 背后首页最大模糊半径；在多少进度内升满。 */
private val BACKDROP_BLUR_MAX = 16.dp
private const val BACKDROP_BLUR_RAMP = 0.35f
/** 模糊在 1/4 分辨率上做：像素量 1/16，模糊后放大看不出差别。 */
private const val BLUR_DOWNSCALE = 0.25f

/** 页面内容在「按钮形态」时的缩放比例（打开从它舒展到 1×，关闭从 1× 收拢回它）。 */
private const val OPEN_MIN_SCALE = 0.94f
private const val SCRIM_MAX_ALPHA = 0.12f
/** 「+」按钮消失时缩到的比例 */
private const val FAB_MIN_SCALE = 0.6f

/** 根层共享状态：按钮位置、本次打开是否来自按钮、过渡进度与当前圆弧。 */
@Stable
class FabRevealState {
    /** 「+」按钮在根布局中的位置（px），由 BottomPillBar 上报。 */
    var fabBounds by mutableStateOf<Rect?>(null)

    /** 点击「+」时置 true，过渡容器读取后清零。 */
    var fromFab by mutableStateOf(false)

    internal var owner by mutableStateOf<Any?>(null)
    internal var progressSource by mutableStateOf<(() -> Float)?>(null)

    // 当前圆弧（根布局坐标，px），由过渡容器在布局完成后写入
    internal var revealCenter by mutableStateOf(Offset.Zero)
    internal var startRadius by mutableFloatStateOf(0f)
    internal var endRadius by mutableFloatStateOf(0f)
    /** 页面容器左边缘（根坐标，px），用于判断收拢中的页面是否已盖住胶囊。 */
    internal var containerLeft by mutableFloatStateOf(0f)

    /** 容器变换进行中或页面已由按钮展开（关闭时缩回按钮）。 */
    val isActive: Boolean get() = owner != null

    /** 0 = 按钮形态，1 = 全屏页面。只在绘制阶段读取。 */
    val progress: Float get() = progressSource?.invoke() ?: 0f

    /**
     * 底栏是否需要在屏：只在过渡**进行中**（p < 1）保留。
     * 页面完全展开后必须移除 —— 透明但仍在最上层的底栏会吞掉添加页底部区域的点击。
     */
    val barVisible: Boolean get() = owner != null && progress < 1f

    /** 当前圆弧半径（px）。 */
    val revealRadius: Float get() = startRadius + (endRadius - startRadius) * progress

    /** 「+」按钮的淡出进度：与页面出现完全同步。 */
    val fabAlpha: Float get() = if (isActive) 1f - progress else 1f
    val fabScale: Float get() = if (isActive) 1f - (1f - FAB_MIN_SCALE) * progress else 1f

    /** 当前页面内容的缩放比例（以按钮中心为原点）。打开 / 关闭对称，只由进度决定，中途反向也连续。 */
    internal fun contentScale(): Float = OPEN_MIN_SCALE + (1f - OPEN_MIN_SCALE) * progress

    /**
     * 某块区域（如导航胶囊，根坐标）的透明度：被页面盖住的过程中由 1 淡到 0，关闭时反向淡回。
     * 「盖住」= 圆弧已扫过 且 （关闭收拢时）缩放后的页面左边缘也越过了这块区域。
     */
    fun alphaUnderReveal(bounds: Rect): Float {
        if (!isActive || endRadius <= 0f || bounds.isEmpty) return 1f
        val c = revealCenter
        val nx = c.x.coerceIn(bounds.left, bounds.right)
        val ny = c.y.coerceIn(bounds.top, bounds.bottom)
        val near = hypot(c.x - nx, c.y - ny)
        val far = max(
            max(hypot(c.x - bounds.left, c.y - bounds.top), hypot(c.x - bounds.right, c.y - bounds.top)),
            max(hypot(c.x - bounds.left, c.y - bounds.bottom), hypot(c.x - bounds.right, c.y - bounds.bottom)),
        )
        val r = revealRadius
        val byCircle = if (far <= near) {
            if (r >= far) 0f else 1f
        } else {
            1f - ((r - near) / (far - near)).coerceIn(0f, 1f)
        }
        val s = contentScale()
        val pageLeft = c.x - (c.x - containerLeft) * s
        val byPage = if (bounds.width <= 0f) 1f
        else ((pageLeft - bounds.left) / bounds.width).coerceIn(0f, 1f)
        return max(byCircle, byPage)
    }
}

/**
 * 圆弧扩散包装。放在 AnimatedContent 的内容里，且该次切换需配
 * `EnterTransition.None togetherWith ExitTransition.None`（动画由这里的 transition 承担，
 * AnimatedContent 会等它播完才移除退场内容）。
 *
 * @param exitToFab 退场时是否缩回按钮（关闭 → true；被下一页替换 → false，保持全屏交给默认转场）
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedVisibilityScope.FabRevealContainer(
    state: FabRevealState,
    exitToFab: () -> Boolean,
    /** 圆内兜底底色，需与页面自身底色一致（CategorySelectScreen 用 surface）。 */
    pageColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    // 进入时确定本次是否走圆弧扩散，之后不再变化（hook 结构固定）
    val startBounds = remember { if (state.fromFab) state.fabBounds else null }
    if (startBounds == null) {
        // 非按钮入口（或按钮位置未知）：普通转场；顺手清掉可能残留的标记
        DisposableEffect(state) {
            state.fromFab = false
            onDispose { }
        }
        content()
        return
    }

    val progressState = transition.animateFloat(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) {
                tween(REVEAL_OPEN_MS, easing = RevealOpenEasing)
            } else {
                tween(REVEAL_CLOSE_MS, easing = RevealCloseEasing)
            }
        },
        label = "fabReveal",
    ) { s ->
        when (s) {
            EnterExitState.PreEnter -> 0f
            EnterExitState.Visible -> 1f
            EnterExitState.PostExit -> if (exitToFab()) 0f else 1f
        }
    }

    DisposableEffect(state) {
        val token = Any()
        state.owner = token
        state.progressSource = { progressState.value }
        state.fromFab = false
        onDispose {
            // 快速关了又开时新容器已接管，旧容器不能把它清掉
            if (state.owner === token) {
                state.owner = null
                state.progressSource = null
                state.endRadius = 0f
            }
        }
    }

    var origin by remember { mutableStateOf(Offset.Zero) }
    val startRadius = startBounds.minDimension / 2f

    Box(
        Modifier
            .fillMaxSize()
            // 与按钮交接：最后 15% 页面整体淡出（打开时最初 15% 淡入）
            .graphicsLayer { alpha = (progressState.value / PAGE_FADE_PHASE).coerceIn(0f, 1f) }
            .onGloballyPositioned { coords ->
                origin = coords.positionInRoot()
                // 终点半径 = 圆心到容器最远角（即左上角）的距离，保证圆弧最终盖满整页
                val c = startBounds.center - origin
                val w = coords.size.width.toFloat()
                val h = coords.size.height.toFloat()
                state.revealCenter = startBounds.center
                state.containerLeft = origin.x
                state.startRadius = startRadius
                state.endRadius = max(
                    max(hypot(c.x, c.y), hypot(w - c.x, c.y)),
                    max(hypot(c.x, h - c.y), hypot(w - c.x, h - c.y)),
                )
            }
            .drawWithContent {
                val p = progressState.value
                if (p >= 1f) {
                    drawContent()
                    return@drawWithContent
                }
                if (p > 0f) drawRect(Color.Black, alpha = SCRIM_MAX_ALPHA * p)

                val center = startBounds.center - origin
                val radius = startRadius + (state.endRadius - startRadius) * p
                val circle = Path().apply { addOval(Rect(center = center, radius = radius)) }
                clipPath(circle) {
                    // 先铺页面底色：缩放后的内容与圆之间若有缝，也不会透出首页（避免两层背景叠加）
                    drawRect(pageColor)
                    // 打开 0.94× → 1× 轻微舒展；关闭 1× → 0.94× 轻微收拢
                    val s = state.contentScale()
                    withTransform({ scale(s, s, pivot = center) }) {
                        this@drawWithContent.drawContent()
                    }
                }
            },
    ) {
        content()
    }
}

/**
 * 背后首页模糊：加在首页（tab 内容）容器上。
 *
 * ── v2：快照 + 低分辨率固定模糊 + 透明度渐变 ──────────────────
 * v1 每帧改 BlurEffect 半径，作用在整个首页上：每一帧都要以全分辨率重建模糊、重新渲染整页，
 * 加上底栏玻璃还要再从这层取样模糊一次，模糊 → 清晰这段明显掉帧。
 * v2 改为：
 * 1. 过渡开始时把首页截一张图（首页在过渡期间是静止的）；
 * 2. 截图缩到 1/4 分辨率，用**固定**半径模糊，只录制一次（像素量只有原来的 1/16，且之后不再重算）；
 * 3. 每帧只改这层模糊图的透明度，放大盖在清晰首页上 —— 帧内工作只剩一次贴图，不再重算模糊。
 * 视觉上是「清晰 ↔ 模糊」平滑交叉淡化，与逐帧改半径几乎无差别。
 */
@Composable
fun Modifier.fabRevealBackdropBlur(state: FabRevealState): Modifier {
    val contentLayer = rememberGraphicsLayer()
    val blurLayer = rememberGraphicsLayer()
    // 只在绘制阶段读取：变化只触发重绘，不触发重组
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    val recordedFor = remember { arrayOfNulls<ImageBitmap>(1) }
    val blurSize = remember { intArrayOf(1, 1) }

    LaunchedEffect(state) {
        snapshotFlow { state.isActive }.collect { active ->
            snapshot = if (active) runCatching { contentLayer.toImageBitmap() }.getOrNull() else null
            if (!active) recordedFor[0] = null
        }
    }

    return this.drawWithContent {
        contentLayer.record { this@drawWithContent.drawContent() }
        drawLayer(contentLayer)

        val bmp = snapshot ?: return@drawWithContent
        val p = if (state.isActive) state.progress else 0f
        if (p <= 0.001f) return@drawWithContent

        if (recordedFor[0] !== bmp) {
            val w = max(1, (bmp.width * BLUR_DOWNSCALE).roundToInt())
            val h = max(1, (bmp.height * BLUR_DOWNSCALE).roundToInt())
            blurSize[0] = w
            blurSize[1] = h
            val r = BACKDROP_BLUR_MAX.toPx() * BLUR_DOWNSCALE
            blurLayer.renderEffect = BlurEffect(r, r, TileMode.Clamp)
            blurLayer.record(IntSize(w, h)) {
                drawImage(bmp, dstSize = IntSize(w, h))
            }
            recordedFor[0] = bmp
        }

        val k = (p / BACKDROP_BLUR_RAMP).coerceIn(0f, 1f)
        blurLayer.alpha = k * k * (3f - 2f * k) // smoothstep
        val sx = size.width / blurSize[0]
        val sy = size.height / blurSize[1]
        withTransform({ scale(sx, sy, pivot = Offset.Zero) }) {
            drawLayer(blurLayer)
        }
    }
}

/** 吞掉所有触摸（过渡期间底栏仍可见，但不能再被点到）。 */
fun Modifier.consumeAllTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
}
