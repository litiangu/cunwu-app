package com.litiangu.wuji.ui.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.litiangu.wuji.ui.theme.BrandOrange
import com.litiangu.wuji.ui.theme.LocalWujiDarkTheme

/**
 * 底部导航栏（Jiumi 布局）：
 * - 左侧一颗半透明胶囊，内含「主页 / 分析 / 设置」三个图标带文字的 tab。
 * - 右侧一颗独立圆形按钮「+」，与胶囊同高、同玻璃语言，不嵌在导航栏内部。
 * - 选中态由滑动指示块承担，双弹簧让块体在途中拉伸；图标选中时 1.16× 弹跳。
 * - 尺寸集中在下方常量，改宽度改 [BarMarginH]，改高度改 [BarHeight] / [FabSize]。
 */
@Composable
fun BottomPillBar(
    items: List<BottomTab>,
    selected: BottomTab,
    onSelect: (BottomTab) -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 整页录制层。传入后胶囊会取它在该区域的模糊副本垫底，形成真实玻璃感。 */
    backdrop: GraphicsLayer? = null,
    /** 「+」按钮在根布局中的位置，供添加页做「从按钮展开 / 缩回按钮」的容器变换。 */
    onFabPositioned: ((Rect) -> Unit)? = null,
    /** 「+」↔ 添加页 圆弧扩散状态：驱动「+」同步淡出 / 淡入、胶囊被圆弧扫过时淡出。 */
    revealState: FabRevealState? = null,
) {
    val isDark = LocalWujiDarkTheme.current
    val context = LocalContext.current
    val selectedIndex = items.indexOf(selected).coerceAtLeast(0)

    // 半透明底色：浅色用白 74%，深色用近黑 60%，让下方内容隐约透出。
    // 浅色不用纯白：白底上纯白胶囊会彻底融掉，带一点冷灰才能在白色卡片间立住。
    val capsuleColor = if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.52f)
    else Color(0xFFF4F4F7).copy(alpha = 0.62f)
    // 边沿比底色更亮一档，模拟玻璃边缘聚光。
    val rimColor = if (isDark) Color.White.copy(alpha = 0.14f)
    else Color.White.copy(alpha = 0.70f)
    val pillColor = if (isDark) BrandOrange.copy(alpha = 0.20f)
    else BrandOrange.copy(alpha = 0.13f)

    // 整条共用交互源：按任意 tab 或圆形按钮，整条一起轻微下沉。
    // 玻璃底：从整页图层里裁出胶囊所在的带状区域，模糊后再画到半透明底色之下。
    val frosted = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    var capsuleBounds by remember { mutableStateOf(Rect.Zero) }

    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()

    // ===== 「+」按住柔光（三段式）=====
    // ① 按下：从按钮边缘一圈向外散开 6 团朦胧的橙色柔光团（均匀分布 + 随机抖动，同时出发）
    // ② 150ms 后：外圈约 12dp 宽的橙色柔光用 500ms 慢慢亮起（不会突然出现）
    // ③ 约 550ms 起：进入随机柔光 —— 外圈随机方向一团团冒出、向外飘散、先亮后淡
    // 颜色全部限定在橙色系（色相 15°~40°，偏红橙 ~ 偏杏黄），饱和度 0.30~0.55，
    // 每团大、边缘散，峰值透明度 ≤ 0.24，整体朦胧半透明。
    // 松手：已冒出的柔光团在独立协程里自然播完、淡完；外圈柔光 450ms 慢慢淡出。
    // 「+」用独立交互源，才能单独知道它是否被按住；整条下沉仍由两者任一触发。
    val fabPress = remember { MutableInteractionSource() }
    val fabPressed by fabPress.collectIsPressedAsState()
    val haloScope = rememberCoroutineScope()
    val haloPuffs = remember { mutableStateListOf<HaloPuff>() }
    var haloGlowOn by remember { mutableStateOf(false) }
    // 只在绘制阶段读 .value，柔光动画不触发底栏重组
    val haloGlow = animateFloatAsState(
        targetValue = if (haloGlowOn) 1f else 0f,
        animationSpec = if (haloGlowOn) {
            tween(HaloGlowInMs, easing = FastOutSlowInEasing)
        } else {
            tween(HaloGlowOutMs, easing = LinearOutSlowInEasing)
        },
        label = "fabGlow",
    )
    fun emit(puff: HaloPuff) {
        haloPuffs.add(puff)
        haloScope.launch {
            try {
                puff.progress.animateTo(1f, tween(puff.durationMs, easing = LinearEasing))
            } finally {
                haloPuffs.remove(puff)
            }
        }
    }
    LaunchedEffect(fabPressed) {
        if (!fabPressed) {
            haloGlowOn = false
            return@LaunchedEffect
        }
        // ① 一圈柔光团同时散开
        val offset = Random.nextFloat() * 2f * PI.toFloat()
        repeat(HaloBurstCount) { i ->
            val angle = offset + i * 2f * PI.toFloat() / HaloBurstCount +
                (Random.nextFloat() - 0.5f) * 0.5f
            emit(HaloPuff.burst(angle))
        }
        // ② 外圈柔光慢慢亮起
        delay(HaloGlowDelayMs)
        haloGlowOn = true
        // ③ 随机柔光
        delay(HaloPuffStartDelayMs)
        while (true) {
            emit(HaloPuff.random())
            delay(Random.nextLong(HaloPuffMinGapMs, HaloPuffMaxGapMs))
        }
    }

    val sink by animateFloatAsState(
        targetValue = if (pressed || fabPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "barSink",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = BarMarginH)
            .height(BarHeight)
            .graphicsLayer { scaleX = sink; scaleY = sink },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onGloballyPositioned { capsuleBounds = it.boundsInRoot() }
                // 圆弧扩散的边缘扫过胶囊时淡出（像被页面盖住），关闭时反向淡回
                .graphicsLayer { alpha = revealState?.alphaUnderReveal(capsuleBounds) ?: 1f }
                // 两段阴影：大范围柔和外扩 + 贴边一层紧的，让胶囊在浅色与深色页面上都立得住。
                .shadow(
                    elevation = 20.dp,
                    shape = CircleShape,
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.60f else 0.16f),
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.50f else 0.10f),
                )
                .clip(CircleShape)
                .onGloballyPositioned { origin = it.positionInRoot() }
                .drawBehind {
                    if (backdrop != null) {
                        // 外扩 24dp 让边缘取到胶囊之外的内容，模糊后不会出现透明边。
                        val margin = 12.dp.toPx()
                        val blur = 10.dp.toPx()
                        frosted.renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
                        frosted.topLeft = IntOffset(-margin.toInt(), -margin.toInt())
                        frosted.record(
                            IntSize(
                                (size.width + 2 * margin).toInt(),
                                (size.height + 2 * margin).toInt(),
                            )
                        ) {
                            translate(margin - origin.x, margin - origin.y) { drawLayer(backdrop) }
                        }
                        drawLayer(frosted)
                    }
                }
                .background(capsuleColor)
                // 上沿一道极轻的高光：深色胶囊靠它透出玻璃厚度，浅色胶囊几乎无变化。
                .drawWithContent {
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = if (isDark) 0.10f else 0.35f),
                            0.55f to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                        size = size,
                    )
                    drawContent()
                }
                .border(1.dp, rimColor, CircleShape),
        ) {
            val tabWidth = maxWidth / items.size

            // 指示块位移：左缘与右缘各一条弹簧，前进时右缘先走、左缘慢跟，块体途中被拉长。
            var fromIndex by remember { mutableIntStateOf(selectedIndex) }
            val forward = selectedIndex >= fromIndex
            LaunchedEffect(selectedIndex) { fromIndex = selectedIndex }
            val lead = spring<Dp>(dampingRatio = 0.82f, stiffness = 700f)
            val trail = spring<Dp>(dampingRatio = 0.72f, stiffness = 230f)
            val targetLeft = tabWidth * selectedIndex
            val left by animateDpAsState(
                targetValue = targetLeft,
                animationSpec = if (forward) trail else lead,
                label = "pillLeft",
            )
            val right by animateDpAsState(
                targetValue = targetLeft + tabWidth,
                animationSpec = if (forward) lead else trail,
                label = "pillRight",
            )

            Box(
                modifier = Modifier
                    .offset(x = left)
                    .width((right - left).coerceAtLeast(0.dp))
                    .fillMaxHeight()
                    .padding(PillInset)
                    .clip(CircleShape)
                    .background(pillColor),
            )

            Row(
                modifier = Modifier.fillMaxSize().selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { tab ->
                    TabItem(
                        modifier = Modifier.weight(1f),
                        tab = tab,
                        selected = tab == selected,
                        interactionSource = press,
                        onClick = {
                            onSelect(tab)
                            context.fireHapticIfEnabled()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.width(BarGap))

        // 独立圆形按钮：与胶囊同高，实心品牌色，边沿一道亮环与胶囊同语言。
        Box(
            modifier = Modifier
                .size(FabSize)
                .onGloballyPositioned { coords -> onFabPositioned?.invoke(coords.boundsInRoot()) }
                // 圆弧扩散打开添加页时，「+」与页面出现同步淡出缩小；关闭时同步重新出现。
                // 放在 onGloballyPositioned 之后：上报给容器的按钮位置不受这里的缩放影响。
                .graphicsLayer {
                    val st = revealState
                    if (st != null) {
                        alpha = st.fabAlpha
                        scaleX = st.fabScale
                        scaleY = st.fabScale
                    }
                }
                // 柔光画在 shadow / clip 之前：不被圆形裁剪，可以画到按钮之外
                .drawBehind {
                    val base = size.minDimension / 2f
                    val c = center
                    // ② 外圈柔光
                    val g = haloGlow.value
                    if (g > 0.001f) {
                        val gr = base + HaloGlowSpread.toPx() * g
                        drawCircle(
                            brush = Brush.radialGradient(
                                (base / gr) to BrandOrange.copy(alpha = 0.30f * g),
                                1f to Color.Transparent,
                                center = c,
                                radius = gr,
                            ),
                            radius = gr,
                            center = c,
                        )
                    }
                    // ①③ 柔光团：两段衰减的径向渐变，边缘化开、朦胧
                    haloPuffs.forEach { puff ->
                        val t = puff.progress.value
                        val env = sin(PI.toFloat() * t) // 先亮后淡
                        if (env <= 0.001f) return@forEach
                        val dist = base + puff.travel.toPx() * t
                        val pc = Offset(c.x + cos(puff.angle) * dist, c.y + sin(puff.angle) * dist)
                        val pr = puff.size.toPx() * (0.65f + 0.35f * t)
                        val pa = HaloPuffMaxAlpha * env
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to puff.color.copy(alpha = pa),
                                0.5f to puff.color.copy(alpha = pa * 0.45f),
                                1f to Color.Transparent,
                                center = pc,
                                radius = pr,
                            ),
                            radius = pr,
                            center = pc,
                        )
                    }
                }
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    spotColor = BrandOrange.copy(alpha = 0.45f),
                    ambientColor = BrandOrange.copy(alpha = 0.25f),
                )
                .clip(CircleShape)
                .background(BrandOrange)
                .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape)
                .clearAndSetSemantics {
                    contentDescription = "添加"
                    role = Role.Button
                    onClick {
                        onFabClick()
                        true
                    }
                }
                .clickable(
                    interactionSource = fabPress,
                    indication = null,
                    onClick = {
                        onFabClick()
                        context.fireHapticIfEnabled()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = WujiIcons.Plus,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * 单个 tab：图标在上、文字在下。选中时图标弹跳、颜色加深、字重加粗。
 */
@Composable
private fun TabItem(
    modifier: Modifier = Modifier,
    tab: BottomTab,
    selected: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        label = "tabColor",
    )
    // 图标选中时先胀到 1.16×，再回落到 1×。
    val pop = remember { Animatable(1f) }
    // 首次组合不弹跳：底栏被重新挂载（如从添加页收回时）不应让选中图标凭空跳一下
    val firstRun = remember { booleanArrayOf(true) }
    LaunchedEffect(selected) {
        if (firstRun[0]) {
            firstRun[0] = false
            return@LaunchedEffect
        }
        if (selected) {
            pop.animateTo(1.16f, tween(90))
            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 500f))
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clearAndSetSemantics {
                contentDescription = tab.label
                this.role = Role.Tab
                this.selected = selected
                this.onClick {
                    onClick()
                    true
                }
            }
            .selectable(
                selected = selected,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            tab.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier
                .size(IconSize)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value },
        )
        Text(
            text = tab.label,
            color = contentColor,
            fontSize = LabelSize,
            lineHeight = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

data class BottomTab(
    val label: String,
    val icon: ImageVector,
)

// ===== 尺寸：改这里即可调整底栏宽高 =====
/** 整条底栏高度，胶囊与圆形按钮同高。 */
private val BarHeight: Dp = 56.dp
/** 距屏幕左右边距，越大胶囊越短。 */
private val BarMarginH: Dp = 16.dp
/** 胶囊与圆形按钮之间的间距。 */
private val BarGap: Dp = 10.dp
/** 圆形按钮直径。 */
private val FabSize: Dp = 56.dp
/** tab 图标尺寸。 */
private val IconSize: Dp = 22.dp
/** tab 文字尺寸。 */
private val LabelSize: TextUnit = 10.sp
/** 指示块相对胶囊边沿的内缩。 */
private val PillInset: Dp = 3.dp

// ===== 「+」按住柔光 =====
/** ① 按下时一圈同时散开的柔光团数量。 */
private const val HaloBurstCount = 6
/** ② 按下后多久开始亮起外圈柔光；亮起 / 淡出时长；外圈柔光宽度。 */
private const val HaloGlowDelayMs = 150L
private const val HaloGlowInMs = 500
private const val HaloGlowOutMs = 450
private val HaloGlowSpread: Dp = 12.dp
/** ③ 外圈柔光开始亮起后多久进入随机柔光；两团之间的随机间隔；峰值透明度（越小越淡）。 */
private const val HaloPuffStartDelayMs = 400L
private const val HaloPuffMinGapMs = 120L
private const val HaloPuffMaxGapMs = 320L
private const val HaloPuffMaxAlpha = 0.24f

/** 一团柔光：外圈某个方向、橙色系低饱和随机色，向外飘并先亮后淡。 */
private class HaloPuff(
    /** 出现方向（弧度）。 */
    val angle: Float,
    val color: Color,
    /** 向外飘的距离。 */
    val travel: Dp,
    /** 柔光团最大半径。 */
    val size: Dp,
    val durationMs: Int,
) {
    val progress = Animatable(0f)

    companion object {
        // 色相限定在橙色系 15°~40°（偏红橙 ~ 偏杏黄），饱和度 0.30~0.55 → 柔和、不艳
        private fun orangeTone(): Color = Color.hsv(
            hue = 15f + Random.nextFloat() * 25f,
            saturation = 0.30f + Random.nextFloat() * 0.25f,
            value = 1f,
        )

        /** ① 按下瞬间那一圈：飘得更远、更快一些，形成「从边缘散开」的第一下。 */
        fun burst(angle: Float): HaloPuff = HaloPuff(
            angle = angle,
            color = orangeTone(),
            travel = (14f + Random.nextFloat() * 8f).dp,
            size = (16f + Random.nextFloat() * 8f).dp,
            durationMs = 700 + Random.nextInt(250),
        )

        /** ③ 随机阶段：方向、大小、时长都随机，飘得近、停留久，更朦胧。 */
        fun random(): HaloPuff = HaloPuff(
            angle = Random.nextFloat() * 2f * PI.toFloat(),
            color = orangeTone(),
            travel = (6f + Random.nextFloat() * 10f).dp,
            size = (14f + Random.nextFloat() * 10f).dp,
            durationMs = 800 + Random.nextInt(500),
        )
    }
}
