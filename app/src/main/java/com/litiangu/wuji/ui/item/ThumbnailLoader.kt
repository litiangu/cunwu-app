package com.litiangu.wuji.ui.item

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 列表 / 详情图片加载：后台解码 + 按显示尺寸降采样 + 内存缓存。
 *
 * ── 为什么需要 ────────────────────────────────────────────────
 * 原先各卡片在组合阶段直接调 decodeImagePath()：
 * 1. **主线程**解码；2. 按**原图尺寸**解码（手机照片 3000~4000px，单张几十 MB 内存）；
 * 3. 没有 remember，**每次重组都重新解码一遍**。
 * 滚动、切换视图、筛选重排、主题切换都会触发大量重组，于是首页明显掉帧。
 *
 * ── 做法 ──────────────────────────────────────────────────────
 * - 按目标显示尺寸算 inSampleSize（2 的幂），只解码到「刚好够清晰」的分辨率：
 *   56dp 缩略图约 1/16~1/32 的像素量。
 * - 在 IO 线程解码，结果放进 LruCache（上限为进程可用内存的 1/8）。
 * - 缓存命中时**组合当帧同步返回**：滚回来、切视图、重排时图片直接就在，不闪占位。
 * - 未命中时先返回 null（调用方显示分类图标占位），解码完成后由调用方淡入。
 *
 * 缓存键 = 路径 + 目标像素。图片文件名带时间戳与随机数（见 generateImageFileName），
 * 编辑换图 / 重新抠图都会产生新路径，不存在「同路径内容变了」的情况；删除时 [ThumbnailCache.evict] 清理。
 */
object ThumbnailCache {
    private val cache = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width * value.height * 4).coerceAtLeast(1)
    }

    private fun key(path: String, targetPx: Int) = "$path@$targetPx"

    fun get(path: String, targetPx: Int): ImageBitmap? = cache.get(key(path, targetPx))

    internal fun put(path: String, targetPx: Int, bmp: ImageBitmap) {
        cache.put(key(path, targetPx), bmp)
    }

    /** 文件被删除时清掉该路径的所有尺寸。 */
    fun evict(path: String?) {
        if (path.isNullOrBlank()) return
        val prefix = "$path@"
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach { cache.remove(it) }
    }
}

/**
 * 按目标尺寸降采样解码：保证解码后长边 ≥ targetPx（ContentScale.Fit 下不糊），
 * 其余像素全部省掉。必须在后台线程调用。
 */
fun decodeImageScaled(path: String?, targetPx: Int): ImageBitmap? {
    if (path.isNullOrBlank() || targetPx <= 0) return null
    ThumbnailCache.get(path, targetPx)?.let { return it }
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val longSide = maxOf(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) return null
        var sample = 1
        while (longSide / (sample * 2) >= targetPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()?.also {
            ThumbnailCache.put(path, targetPx, it)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/** [rememberThumbnail] 的结果：图片（未就绪为 null）+ 建议透明度（异步加载完成时淡入）。 */
@Stable
class ThumbnailState internal constructor(initial: ImageBitmap?) {
    var bitmap by mutableStateOf(initial)
        internal set

    /** 缓存命中直接为 1；异步加载完成后 0 → 1 淡入。只应在 graphicsLayer 中读取。 */
    internal val alphaAnim = Animatable(if (initial != null) 1f else 0f)
    val alpha: Float get() = alphaAnim.value
}

/**
 * 在组合中获取一张按显示尺寸解码的图片。
 * @param maxSide 图片显示区域的长边（如 56.dp 缩略图、卡片列宽）。
 */
@Composable
fun rememberThumbnail(path: String?, maxSide: Dp): ThumbnailState {
    val targetPx = with(LocalDensity.current) { maxSide.roundToPx() }
    val state = remember(path, targetPx) {
        ThumbnailState(if (path.isNullOrBlank()) null else ThumbnailCache.get(path, targetPx))
    }
    LaunchedEffect(state) {
        if (state.bitmap != null || path.isNullOrBlank()) return@LaunchedEffect
        val bmp = withContext(Dispatchers.IO) { decodeImageScaled(path, targetPx) }
        if (bmp != null) {
            state.bitmap = bmp
            state.alphaAnim.animateTo(1f, tween(THUMB_FADE_MS))
        }
    }
    return state
}

/** [rememberThumbnailHolding] 的内部持有者：保存最近一次成功加载的图片。 */
@Stable
private class HeldThumbnail {
    var bitmap by mutableStateOf<ImageBitmap?>(null)
}

/**
 * 「换图不闪」版：路径变化、新图还在后台解码时，**继续显示上一张图**，解码完成后直接替换。
 * 适合同一位置会频繁换图的地方（编辑页原图 ↔ 抠图切换、换头像）——普通版此时会先闪回占位图标。
 * 路径为空（未选图 / 已移除）时立即返回 null。
 * @param maxSide 图片显示区域的长边。
 */
@Composable
fun rememberThumbnailHolding(path: String?, maxSide: Dp): ImageBitmap? {
    val targetPx = with(LocalDensity.current) { maxSide.roundToPx() }
    val held = remember { HeldThumbnail() }
    val cached = remember(path, targetPx) {
        if (path.isNullOrBlank()) null else ThumbnailCache.get(path, targetPx)
    }
    LaunchedEffect(path, targetPx) {
        if (path.isNullOrBlank()) {
            held.bitmap = null
            return@LaunchedEffect
        }
        val bmp = cached ?: withContext(Dispatchers.IO) { decodeImageScaled(path, targetPx) }
        held.bitmap = bmp
    }
    return if (path.isNullOrBlank()) null else cached ?: held.bitmap
}

private const val THUMB_FADE_MS = 160
