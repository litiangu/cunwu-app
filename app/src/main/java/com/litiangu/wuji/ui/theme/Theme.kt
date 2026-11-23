package com.litiangu.wuji.ui.theme

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import com.litiangu.wuji.data.settings.DarkModePref
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max

/**
 * 主题切换全局过渡：圆形扩散（主），快照交叉淡化（兜底）。
 *
 * ── 圆形扩散 ──────────────────────────────────────────────────
 * 与淡化共用同一套「旧主题截图盖在最上层」的机制，区别只在截图怎么退场：
 * 截图上挖一个从点击处（深色模式选项中心）不断变大的圆洞，洞里露出底下已切好的新主题，
 * 直到圆覆盖屏幕四角。半径 0 → 到最远屏幕角的距离，520ms，Material 标准曲线
 * （面积随半径平方增长，前段加速 + 后段减速，看起来匀称不拖尾）。
 * 系统栏图标在圆完全盖住屏幕顶边（两个顶角）那一刻切明暗，与画面同步。
 * 调用 animateNextChange 时未给出 origin 则退回淡化。
 *
 * ── 交叉淡化（兜底）────────────────────────────────────────────
 *
 * ── 为什么不逐个颜色做 animateColorAsState ─────────────────────
 * 1. 每帧都要换一份 ColorScheme → 全 App 每帧重组，debug 包 / 模拟器上必掉帧；
 * 2. 只覆盖 MaterialTheme 的颜色，BrandOrange、LocalTints 分类色、字体颜色、图片抠图底等
 *    硬编码或非 colorScheme 的颜色仍会瞬间跳变，新旧混在一起反而更乱。
 *
 * ── 做法 ──────────────────────────────────────────────────────
 * 切换前把整屏截一张图（GraphicsLayer.toImageBitmap，只是复用已录制的绘制指令，开销很小），
 * 盖在最上层；新主题在底下**只重组一次**，然后截图在 380ms 内淡出。所有颜色一起、
 * 一致地过渡，与颜色来源无关。系统栏图标无法动画，放在淡出过半时再切明暗，
 * 避免白色图标先压在还没变暗的浅色截图上。
 *
 * ── 何时播放 ──────────────────────────────────────────────────
 * 只有调用过 [ThemeTransitionState.animateNextChange] 后 1s 内发生的切换才播放（设置页点选时调用）。
 * 冷启动时 DataStore 由初值跳到真实偏好也会让 darkTheme 变一次，这种变化必须直接生效，
 * 否则每次打开 App 都会闪一下淡化。
 */
private const val THEME_FADE_MS = 380
private const val THEME_REVEAL_MS = 520
// Material 标准曲线 FastOutSlowIn
private val ThemeRevealEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
private const val ARM_WINDOW_MS = 1_000L
// Material 3 emphasized decelerate：开头快速揭开、尾段柔和收住
private val ThemeFadeEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

@Stable
class ThemeTransitionState {
    private var armedAt = 0L
    private var armedOrigin: Offset? = null

    internal class Armed(val origin: Offset?)

    /**
     * 声明「接下来的这次主题变化由用户触发，请播放过渡」。
     * @param origin 圆形扩散的圆心，**根布局坐标**（px，如 boundsInRoot().center）；null = 用淡化。
     */
    fun animateNextChange(origin: Offset? = null) {
        armedAt = SystemClock.uptimeMillis()
        armedOrigin = origin
    }

    internal fun consume(): Armed? {
        val ok = armedAt != 0L && SystemClock.uptimeMillis() - armedAt < ARM_WINDOW_MS
        val result = if (ok) Armed(armedOrigin) else null
        armedAt = 0L
        armedOrigin = null
        return result
    }
}

val LocalThemeTransition = staticCompositionLocalOf { ThemeTransitionState() }

/**
 * 乐观主题偏好：设置页点选后立即写入，WujiApp 优先用它决定 darkTheme，
 * 不必等 DataStore 落盘再回流（省掉一次磁盘 IO 往返，圆形扩散能马上开始）。
 * DataStore 回流的值与之一致后由 WujiApp 清空，之后仍以 DataStore 为准。
 */
object PendingDarkMode {
    var pref by mutableStateOf<DarkModePref?>(null)
}

@Composable
fun WujiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val transition = remember { ThemeTransitionState() }
    // 实际生效的主题（滞后于 darkTheme：先截图，再切换）
    var appliedDark by remember { mutableStateOf(darkTheme) }
    // 系统栏图标明暗（再滞后半程）
    var barsDark by remember { mutableStateOf(darkTheme) }
    val screenLayer = rememberGraphicsLayer()
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    val snapshotAlpha = remember { Animatable(0f) }
    // 圆形扩散：圆心（null = 走淡化）与当前半径（px）
    var revealOrigin by remember { mutableStateOf<Offset?>(null) }
    val revealRadius = remember { Animatable(0f) }

    LaunchedEffect(darkTheme) {
        if (darkTheme == appliedDark) {
            snapshot = null
            revealOrigin = null
            barsDark = darkTheme
            return@LaunchedEffect
        }
        val armed = transition.consume()
        val bmp = if (armed != null) {
            runCatching { screenLayer.toImageBitmap() }.getOrNull()
        } else null
        if (armed == null || bmp == null) {
            // 非用户触发（冷启动 / 截图失败）：直接生效
            snapshot = null
            revealOrigin = null
            appliedDark = darkTheme
            barsDark = darkTheme
            return@LaunchedEffect
        }
        val origin = armed.origin
        if (origin != null) revealRadius.snapTo(0f) else snapshotAlpha.snapTo(1f)
        revealOrigin = origin
        snapshot = bmp
        appliedDark = darkTheme
        // 等一帧：让新主题在截图底下先完成重组与首帧绘制，动画从第一帧起就是丝滑的
        withFrameNanos { }
        if (origin != null) {
            val w = bmp.width.toFloat()
            val h = bmp.height.toFloat()
            // 终点半径 = 圆心到最远屏幕角的距离，保证完全覆盖
            val maxR = max(
                max(hypot(origin.x, origin.y), hypot(w - origin.x, origin.y)),
                max(hypot(origin.x, h - origin.y), hypot(w - origin.x, h - origin.y)),
            )
            // 圆盖住两个顶角 = 状态栏整条已是新主题，此刻切系统栏图标
            val topCoverR = max(hypot(origin.x, origin.y), hypot(w - origin.x, origin.y))
            var barsSwitched = false
            revealRadius.animateTo(maxR, tween(THEME_REVEAL_MS, easing = ThemeRevealEasing)) {
                if (!barsSwitched && value >= topCoverR) {
                    barsSwitched = true
                    barsDark = darkTheme
                }
            }
            barsDark = darkTheme
        } else {
            coroutineScope {
                launch { snapshotAlpha.animateTo(0f, tween(THEME_FADE_MS, easing = ThemeFadeEasing)) }
                launch {
                    delay(THEME_FADE_MS / 3L)
                    barsDark = darkTheme
                }
            }
        }
        snapshot = null
        revealOrigin = null
    }

    val darkTheme = appliedDark
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = PrimaryDark,
            onPrimary = OnPrimaryDark,
            primaryContainer = PrimaryContainerDark,
            onPrimaryContainer = OnPrimaryContainerDark,
            secondary = SecondaryDark,
            onSecondary = OnSecondaryDark,
            secondaryContainer = SecondaryContainerDark,
            onSecondaryContainer = OnSecondaryContainerDark,
            tertiary = TertiaryDark,
            onTertiary = OnTertiaryDark,
            tertiaryContainer = TertiaryContainerDark,
            onTertiaryContainer = OnTertiaryContainerDark,
            background = BackgroundDark,
            onBackground = OnBackgroundDark,
            surface = SurfaceDark,
            onSurface = OnSurfaceDark,
            surfaceVariant = SurfaceVariantDark,
            onSurfaceVariant = OnSurfaceVariantDark,
            surfaceContainerLowest = SurfaceContainerLowestDark,
            surfaceContainerLow = SurfaceContainerLowDark,
            surfaceContainer = SurfaceContainerDark,
            surfaceContainerHigh = SurfaceContainerHighDark,
            surfaceContainerHighest = SurfaceContainerHighestDark,
            error = ErrorDark,
            onError = OnErrorDark,
            errorContainer = ErrorContainerDark,
            onErrorContainer = OnErrorContainerDark,
            outline = OutlineDark,
            outlineVariant = OutlineVariantDark
        )
    } else {
        lightColorScheme(
            primary = PrimaryLight,
            onPrimary = OnPrimaryLight,
            primaryContainer = PrimaryContainerLight,
            onPrimaryContainer = OnPrimaryContainerLight,
            secondary = SecondaryLight,
            onSecondary = OnSecondaryLight,
            secondaryContainer = SecondaryContainerLight,
            onSecondaryContainer = OnSecondaryContainerLight,
            tertiary = TertiaryLight,
            onTertiary = OnTertiaryLight,
            tertiaryContainer = TertiaryContainerLight,
            onTertiaryContainer = OnTertiaryContainerLight,
            background = BackgroundLight,
            onBackground = OnBackgroundLight,
            surface = SurfaceLight,
            onSurface = OnSurfaceLight,
            surfaceVariant = SurfaceVariantLight,
            onSurfaceVariant = OnSurfaceVariantLight,
            surfaceContainerLowest = SurfaceContainerLowestLight,
            surfaceContainerLow = SurfaceContainerLowLight,
            surfaceContainer = SurfaceContainerLight,
            surfaceContainerHigh = SurfaceContainerHighLight,
            surfaceContainerHighest = SurfaceContainerHighestLight,
            error = ErrorLight,
            onError = OnErrorLight,
            errorContainer = ErrorContainerLight,
            onErrorContainer = OnErrorContainerLight,
            outline = OutlineLight,
            outlineVariant = OutlineVariantLight
        )
    }
    // [Step A] 系统栏图标明暗跟随应用内主题。
    // MainActivity 的 enableEdgeToEdge(SystemBarStyle.auto) 依据 Configuration.uiMode 判定明暗，
    // 而应用内深色三档只改 DataStore 不改 uiMode，系统浅色 + 应用内深色时图标会保留深色，
    // 压在深色背景上不可见。这里按 darkTheme 显式覆盖，跟随系统档时 darkTheme 已由
    // isSystemInDarkTheme() 解析，同样正确。
    val view = LocalView.current
    // 窗口底同步：冷启动时由 Activity 先铺一次，运行中切换主题时由这里跟上。
    // 不同步的话，页面过渡的 alpha 空隙会透出资源里的 windowBackground，
    // 而它跟随系统 uiMode，与应用内三档不是一回事。
    val windowBg = colorScheme.background.toArgb()
    val lastWindowBg = remember { mutableStateOf<Int?>(null) }
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !barsDark
            controller.isAppearanceLightNavigationBars = !barsDark
            if (lastWindowBg.value != windowBg) {
                window.setBackgroundDrawable(ColorDrawable(windowBg))
                lastWindowBg.value = windowBg
            }
        }
    }
    CompositionLocalProvider(
        LocalTints provides if (darkTheme) DarkTints else LightTints,
        LocalWujiDarkTheme provides darkTheme,
        LocalThemeTransition provides transition,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = remember(darkTheme) { wujiTypography(darkTheme) },
        ) {
            // 整屏录进 screenLayer 供截图；过渡期间旧主题截图盖在最上层淡出。
            // 截图与透明度都只在绘制阶段读取，淡出过程不触发重组。
            Box(
                modifier = Modifier.drawWithContent {
                    screenLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(screenLayer)
                    val bmp = snapshot
                    if (bmp != null) {
                        val o = revealOrigin
                        if (o != null) {
                            // 旧主题截图挖掉一个圆洞，洞内露出新主题
                            val hole = Path().apply { addOval(Rect(center = o, radius = revealRadius.value)) }
                            clipPath(hole, clipOp = ClipOp.Difference) { drawImage(bmp) }
                        } else {
                            drawImage(bmp, alpha = snapshotAlpha.value)
                        }
                    }
                },
                propagateMinConstraints = true,
            ) {
                content()
            }
        }
    }
}
