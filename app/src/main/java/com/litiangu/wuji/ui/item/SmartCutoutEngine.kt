package com.litiangu.wuji.ui.item

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.graphics.scale
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel

/**
 * Step 16：端侧智能抠图引擎（ormbg-LiteRT + 贴纸化后处理）v2。
 *
 * 使用 ormbg 模型（ISNet 架构，Apache-2.0）通过 Google LiteRT 在设备本地做高质量背景去除，
 * 再叠加贴纸化后处理（白色模切边 + 投影阴影），输出专业贴纸风格的透明 PNG。
 *
 * v2 优化点（对齐 sticker-forge 效果）：
 * - alpha 掩码：γ 校正 + 硬阈值截断，彻底消除背景残留
 * - 白边：真形态学膨胀（3×3 圆形核），边缘清晰锐利
 * - 阴影：仅边缘带投影（dilated − original），非全区域铺底
 * - 模糊：Stack Blur 替代 BlurMaskFilter，跨版本稳定
 *
 * 管线：
 *   原图 → 缩放至 1024×1024 → ormbg 推理（alpha 掩码）
 *     → γ校正 + 阈值截断 → 缩回原图尺寸 → 合成透明前景
 *       → 白边膨胀描边 + 边缘阴影 → 最终贴纸 Bitmap
 *
 * 全程端侧、无联网、不依赖 GMS，国行设备可用。
 * 模型文件 ormbg.tflite（176MB）打包在 app/src/main/assets/。
 */
object SmartCutoutEngine {

    // ormbg TFLite 模型文件名（assets 目录下）
    private const val MODEL_FILE = "ormbg.tflite"

    // ormbg 模型输入尺寸（固定 1024×1024）
    private const val MODEL_SIZE = 1024

    // ── alpha 后处理参数 ──
    /** γ 校正指数 >1 压暗中间调，让前景/背景分离更干脆 */
    private const val GAMMA = 1.6f
    /** 低端硬阈值：alpha 归一化后低于此值的像素强制变全透 */
    private const val ALPHA_THRESHOLD = 0.12f
    /** 高端阈值：高于此值的像素强制不透（兜底） */
    private const val ALPHA_CEILING = 0.92f

    // ── 贴纸效果参数 ──
    /** 白边宽度（dp）：参考 sticker-forge 的 outline 粗细 */
    private const val BORDER_WIDTH_DP = 4f
    /** 阴影模糊半径（dp） */
    private const val SHADOW_BLUR_RADIUS_DP = 8f
    /** 阴影 X 偏移（dp） */
    private const val SHADOW_OFFSET_X_DP = 5f
    /** 阴影 Y 偏移（dp） */
    private const val SHADOW_OFFSET_Y_DP = 6f
    /** 阴影颜色（30% 不透明黑色） */
    private const val SHADOW_ALPHA = 0x4D000000

    /**
     * 对给定原图执行完整抠图+贴纸化管线，返回贴纸风格 Bitmap。
     */
    suspend fun segmentSubject(context: Context, bitmap: Bitmap): Bitmap {
        // 1. 确保 ARGB_8888 格式
        val src = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
            else bitmap.copy(Bitmap.Config.ARGB_8888, false)

        // 2. 缩放到模型输入尺寸（保持宽高比，letterbox 居中）
        val scaled = scaleToFitLetterbox(src, MODEL_SIZE, MODEL_SIZE)

        // 3. 准备输入张量：[1, 3, 1024, 1024] NCHW, RGB, x/255
        val input = FloatArray(1 * 3 * MODEL_SIZE * MODEL_SIZE)
        prepareInput(scaled, input, MODEL_SIZE, MODEL_SIZE)

        // 3.5 模型完整性前置校验：Git LFS 指针文件仅约 130 字节，会让 LiteRT 抛
        // "Failed to load model from buffer"。声明大小小于 1MB 即视为未拉取真实模型。
        val modelFd = context.assets.openFd(MODEL_FILE)
        val modelSize = modelFd.length
        modelFd.close()
        if (modelSize < 1024 * 1024L) {
            throw IllegalStateException(
                "模型文件未下载完整（${modelSize} 字节，疑似 Git LFS 指针）。" +
                "请在本机项目目录执行 git lfs pull 拉取 ormbg.tflite"
            )
        }

        // 4. LiteRT 推理（CPU-only，模拟器 GPU 不可用）
        val options = CompiledModel.Options(Accelerator.CPU)
        val model = CompiledModel.create(context.assets, MODEL_FILE, options)
        try {
            val inBufs = model.createInputBuffers()
            val outBufs = model.createOutputBuffers()

            inBufs[0].writeFloat(input)
            model.run(inBufs, outBufs)

            // 输出：[1, 1, 1024, 1024] alpha matte [0,1]
            val rawAlpha = outBufs[0].readFloat()

            // 5. 后处理：γ校正+阈值 → 缩回原图尺寸 → 合成透明前景
            val cutout = postprocessAlpha(rawAlpha, scaled, src.width, src.height, MODEL_SIZE, MODEL_SIZE)

            // 6. 贴纸化：白边 + 边缘阴影
            val density = context.resources.displayMetrics.density
            val sticker = applyStickerEffect(cutout, density)

            return sticker
        } finally {
            model.close()
        }
    }

    // ══════════════════════════════════════════════════════════
    //  预处理：缩放 + 张量准备
    // ══════════════════════════════════════════════════════════

    /**
     * 将原图等比缩放并 letterbox 居中到目标尺寸。
     * 用深灰色(0x404040)填充 letterbox 区域：与浅色背景物品反差大，
     * 或mbg 更容易区分「填充区=背景」。
     */
    private fun scaleToFitLetterbox(src: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val ratio = minOf(targetW.toFloat() / src.width, targetH.toFloat() / src.height)
        val newW = (src.width * ratio).toInt()
        val newH = (src.height * ratio).toInt()
        val resized = Bitmap.createScaledBitmap(src, newW, newH, true)

        val result = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(0xFF404040.toInt()) // 深灰 letterbox（比浅灰更易被模型识别为背景）
        val left = ((targetW - newW) / 2f)
        val top = ((targetH - newH) / 2f)
        canvas.drawBitmap(resized, left.toFloat(), top.toFloat(), null)
        if (resized !== src) resized.recycle()
        return result
    }

    /**
     * 将 Bitmap 转为 ormbg 输入格式：NCHW [1,3,H,W], RGB, 像素值 /255。
     */
    private fun prepareInput(bitmap: Bitmap, output: FloatArray, w: Int, h: Int) {
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        var idx = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = pixels[y * w + x]
                output[idx]             = ((c shr 16) and 0xFF) / 255f           // R
                output[idx + w * h]     = ((c shr 8) and 0xFF) / 255f            // G
                output[idx + w * h * 2] = (c and 0xFF) / 255f                   // B
                idx++
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  后处理：α 校正 → 透明前景（v2 核心改进）
    // ══════════════════════════════════════════════════════════

    /**
     * 将原始 alpha 掩码转为最终透明前景 Bitmap。
     *
     * v2 改进流程：
     * 1. min-max 归一化到 [0,1]
     * 2. γ 校正（γ>1 压暗中间调，让半透区域更快趋向 0 或 1）
     * 3. 硬阈值截断：<threshold → 0, >ceiling → 255
     * 4. 可选：形态学开运算去噪（3×3 核 erode→dilate）
     * 5. 缩回原图尺寸并用 alpha 合成
     */
    private fun postprocessAlpha(
        rawAlpha: FloatArray,
        modelBitmap: Bitmap,
        origW: Int,
        origH: Int,
        modelW: Int,
        modelH: Int
    ): Bitmap {
        val totalPixels = modelW * modelH

        // 1. Min-max 归一化到 [0, 1]
        var min = Float.MAX_VALUE
        var max = Float.MIN_VALUE
        for (v in rawAlpha) {
            if (v < min) min = v
            if (v > max) max = v
        }
        val range = if (max > min) (max - min) else 1f

        // 2. γ 校正 + 硬阈值截断 → 最终 byte alpha [0, 255]
        val normAlpha = ByteArray(totalPixels)
        for (i in rawAlpha.indices) {
            // 归一化到 [0, 1]
            var v = (rawAlpha[i] - min) / range
            // γ 校正：压暗中间调，让前景/背景分离更干脆
            v = v.toFloat().pow(GAMMA)
            // 硬阈值截断
            if (v < ALPHA_THRESHOLD) v = 0f
            else if (v > ALPHA_CEILING) v = 1f
            normAlpha[i] = (v * 255f).toInt().coerceIn(0, 255).toByte()
        }

        // 3. 形态学开运算去噪（可选，去除孤立噪点）
        morphologicalOpen(normAlpha, modelW, modelH)

        // 4. 用 alpha 合成原图像素 → 模型尺寸的透明 Bitmap
        val modelPixels = IntArray(totalPixels)
        val srcPixels = IntArray(totalPixels)
        modelBitmap.getPixels(srcPixels, 0, modelW, 0, 0, modelW, modelH)
        for (i in modelPixels.indices) {
            val a = normAlpha[i].toInt() and 0xFF
            val c = srcPixels[i]
            modelPixels[i] = (a shl 24) or (c and 0x00FFFFFF)
        }
        val modelCutout = Bitmap.createBitmap(modelPixels, modelW, modelH, Bitmap.Config.ARGB_8888)

        // 5. 反向 letterbox：裁切有效区域并缩回原图尺寸
        val ratio = minOf(modelW.toFloat() / origW, modelH.toFloat() / origH)
        val actualW = (origW * ratio).toInt()
        val actualH = (origH * ratio).toInt()
        val cropLeft = (modelW - actualW) / 2
        val cropTop = (modelH - actualH) / 2

        val cropped = Bitmap.createBitmap(modelCutout, cropLeft, cropTop, actualW, actualH)
        val result = Bitmap.createScaledBitmap(cropped, origW, origH, true)

        if (modelCutout !== result) modelCutout.recycle()
        if (cropped !== result) cropped.recycle()
        return result
    }

    /** 对 byte alpha 掩码做形态学开运算（先腐蚀后膨胀，去除小噪点） */
    private fun morphologicalOpen(alpha: ByteArray, w: Int, h: Int) {
        // 3×3 十字核腐蚀
        val eroded = ByteArray(alpha.size)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                var minVal = 0xFF
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val v = alpha[(y + dy) * w + (x + dx)].toInt() and 0xFF
                        if (v < minVal) minVal = v
                    }
                }
                eroded[idx] = minVal.toByte()
            }
        }
        // 3×3 十字核膨胀
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                var maxVal = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val v = eroded[(y + dy) * w + (x + dx)].toInt() and 0xFF
                        if (v > maxVal) maxVal = v
                    }
                }
                alpha[idx] = maxVal.toByte()
            }
        }
    }

    /** Kotlin stdlib 没有 Float.pow(Float)，手动实现 */
    private fun Float.pow(exp: Float): Float {
        return Math.pow(this.toDouble(), exp.toDouble()).toFloat()
    }

    // ══════════════════════════════════════════════════════════
    //  贴纸化：白边 + 边缘阴影（v2 重写）
    // ══════════════════════════════════════════════════════════

    /**
     * 对透明抠图应用贴纸效果：白色模切边 + 边缘投影阴影。
     *
     * v2 改进：
     * - 白边：真形态学膨胀（3×3 圆形核，多轮迭代），边缘清晰
     * - 阴影：仅边缘带（dilatedAlpha − originalAlpha 的差集），非全区域
     * - 模糊：Stack Blur（O(n) 近似高斯），替代不可靠的 BlurMaskFilter
     *
     * 合成顺序（从底到顶）：阴影层 → 白边层 → 原始前景
     */
    private fun applyStickerEffect(cutout: Bitmap, density: Float): Bitmap {
        val w = cutout.width
        val h = cutout.height
        val borderWidthPx = (BORDER_WIDTH_DP * density).toInt().coerceAtLeast(2)
        val blurRadiusPx = (SHADOW_BLUR_RADIUS_DP * density).toInt().coerceAtLeast(3)
        val shadowOffX = (SHADOW_OFFSET_X_DP * density).toInt()
        val shadowOffY = (SHADOW_OFFSET_Y_DP * density).toInt()

        // 提取 alpha 通道为 byte 数组（高效操作）
        val alphaBytes = extractAlphaBytes(cutout, w, h)

        // 1. 形态学膨胀得到「放大版 alpha」（用于生成白边区域）
        val dilatedBytes = morphologicalDilate(alphaBytes, w, h, borderWidthPx)

        // 2. 计算边缘带 alpha（膨胀版 − 原版 = 仅边缘区域）
        val edgeBytes = ByteArray(alphaBytes.size)
        for (i in edgeBytes.indices) {
            val d = dilatedBytes[i].toInt() and 0xFF
            val o = alphaBytes[i].toInt() and 0xFF
            // 边缘带 = 膨胀后多出来的部分
            edgeBytes[i] = (d - o).coerceAtLeast(0).toByte()
        }

        // 3. 对边缘带做模糊 → 得到柔和阴影形状
        val shadowShape = stackBlur(edgeBytes, w, h, blurRadiusPx)

        // 4. 合成最终贴纸画布（扩展尺寸以容纳阴影偏移）
        val padL = blurRadiusPx + maxOf(0, shadowOffX)
        val padT = blurRadiusPx + maxOf(0, shadowOffY)
        val padR = blurRadiusPx + maxOf(0, -shadowOffX)
        val padB = blurRadiusPx + maxOf(0, -shadowOffY)
        val resultW = w + padL + padR
        val resultH = h + padT + padB

        val result = Bitmap.createBitmap(resultW, resultH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Layer 1（最底）：阴影（仅边缘带，模糊后填半透明黑）
        drawShadowLayer(canvas, shadowShape, w, h, padL, padT, shadowOffX, shadowOffY)

        // Layer 2（中间）：白边（膨胀区域填白）
        drawWhiteBorder(canvas, dilatedBytes, w, h, padL, padT)

        // Layer 3（顶层）：原始前景（覆盖白边内部，保留清晰主体）
        canvas.drawBitmap(cutout, padL.toFloat(), padT.toFloat(), null)

        return result
    }

    // ──── 阴影层绘制 ────

    private fun drawShadowLayer(
        canvas: Canvas, shadowShape: ByteArray,
        srcW: Int, srcH: Int, offsetX: Int, offsetY: Int,
        offX: Int, offY: Int
    ) {
        val pixels = IntArray(srcW * srcH)
        for (i in pixels.indices) {
            val a = (shadowShape[i].toInt() and 0xFF) * 30 / 255 // 缩放到 0~30 (≈12%)
            pixels[i] = (a shl 24) or 0x000000 // RGB 黑色
        }
        val shadowBm = Bitmap.createBitmap(pixels, srcW, srcH, Bitmap.Config.ARGB_8888)
        canvas.drawBitmap(shadowBm, (offsetX + offX).toFloat(), (offsetY + offY).toFloat(), null)
        shadowBm.recycle()
    }

    // ──── 白边层绘制 ────

    private fun drawWhiteBorder(
        canvas: Canvas, dilatedBytes: ByteArray,
        srcW: Int, srcH: Int, offsetX: Int, offsetY: Int
    ) {
        val pixels = IntArray(srcW * srcH)
        for (i in pixels.indices) {
            val a = dilatedBytes[i].toInt() and 0xFF
            pixels[i] = (a shl 24) or 0xFFFFFF // RGB 白色
        }
        val borderBm = Bitmap.createBitmap(pixels, srcW, srcH, Bitmap.Config.ARGB_8888)
        canvas.drawBitmap(borderBm, offsetX.toFloat(), offsetY.toFloat(), null)
        borderBm.recycle()
    }

    // ══════════════════════════════════════════════════════════
    //  图像处理原语
    // ══════════════════════════════════════════════════════════

    /** 提取 Bitmap 的 alpha 通道为 ByteArray */
    private fun extractAlphaBytes(src: Bitmap, w: Int, h: Int): ByteArray {
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val alpha = ByteArray(w * h)
        for (i in pixels.indices) {
            alpha[i] = ((pixels[i] ushr 24) and 0xFF).toByte()
        }
        return alpha
    }

    /**
     * 形态学膨胀：用圆形近似核（3×3 十字 + 四角）迭代多次。
     * radius 越大，膨胀越明显（白边越粗）。
     */
    private fun morphologicalDilate(alpha: ByteArray, w: Int, h: Int, radius: Int): ByteArray {
        var current = alpha
        // 每次迭代膨胀 1px，共 radius 次
        for (iter in 0 until radius) {
            val next = ByteArray(current.size)
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val idx = y * w + x
                    var maxVal = 0
                    // 3×3 邻域最大值（圆形核近似）
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            val v = current[(y + dy) * w + (x + dx)].toInt() and 0xFF
                            if (v > maxVal) maxVal = v
                        }
                    }
                    next[idx] = maxVal.toByte()
                }
            }
            current = next
        }
        return current
    }

    /**
     * Stack Blur：O(n) 近似高斯模糊，对单通道 byte alpha 操作。
     *
     * 算法源于 http://www.quasimondo.com/StackBlurForCanvas/StackBlur.html
     * 先水平方向模糊，再垂直方向模糊，效果接近高斯但速度快得多。
     * 比 BlurMaskFilter 更可控、跨 Android 版本行为一致。
     */
    private fun stackBlur(alpha: ByteArray, w: Int, h: Int, radius: Int): ByteArray {
        if (radius < 1) return alpha.clone()

        val result = alpha.clone()
        val temp = ByteArray(alpha.size)
        val divSum = radius * 2 + 1
        val div = 1f / divSum

        // 水平方向
        for (y in 0 until h) {
            var sum = 0
            // 初始化窗口
            for (i in -radius..radius) {
                val clampedX = i.coerceIn(0, w - 1)
                sum += (alpha[y * w + clampedX].toInt() and 0xFF)
            }
            for (x in 0 until w) {
                result[y * w + x] = (sum * div).toInt().toByte()
                // 滑动窗口：减左边界、加右边界
                val leftX = (x - radius).coerceIn(0, w - 1)
                val rightX = (x + radius + 1).coerceIn(0, w - 1)
                sum -= (alpha[y * w + leftX].toInt() and 0xFF)
                sum += (alpha[y * w + rightX].toInt() and 0xFF)
            }
        }

        // 垂直方向
        for (x in 0 until w) {
            var sum = 0
            for (i in -radius..radius) {
                val clampedY = i.coerceIn(0, h - 1)
                sum += (result[clampedY * w + x].toInt() and 0xFF)
            }
            for (y in 0 until h) {
                temp[y * w + x] = (sum * div).toInt().toByte()
                val topY = (y - radius).coerceIn(0, h - 1)
                val bottomY = (y + radius + 1).coerceIn(0, h - 1)
                sum -= (result[topY * w + x].toInt() and 0xFF)
                sum += (result[bottomY * w + x].toInt() and 0xFF)
            }
        }
        return temp
    }

    private fun maxOf(a: Int, b: Int): Int = if (a > b) a else b
}
