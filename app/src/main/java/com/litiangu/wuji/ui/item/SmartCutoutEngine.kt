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
 * - alpha 掩码：硬边化 + 形态学闭开 + 最大连通域，主体更实、背景更干净
 * - 全分辨率合成：alpha 缩放回原始尺寸后套原图 RGB，避免二次缩放失色
 * - 白边：真形态学膨胀（3×3 圆形核），边缘清晰锐利
 * - 阴影：仅边缘带投影（dilated − original），非全区域铺底
 * - 模糊：Stack Blur 替代 BlurMaskFilter，跨版本稳定
 *
 * 管线：
 *   原图 → 缩放至 1024×1024 → ormbg 推理（alpha 掩码）
 *     → 硬边化 → 开运算去噪 → 均匀背景先验清边 → 洪泛填洞 → 保留最大连通域
 *       → alpha 缩放回原始尺寸 → 套原图 RGB 合成透明前景
 *         → 白边膨胀描边 + 边缘阴影 → 最终贴纸 Bitmap
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
    // 方案 B：窄羽化硬边，浅色主体强制不透明，商品/硬边物品更稳
    /**
     * 低端硬阈值：alpha 归一化后低于此值的像素强制变全透。
     * [修复11] 起兼任「不自信带」下界：0.05~[UNCERTAIN_HIGH] 的像素按颜色贴近局部背景场与否裁决。
     * 历史教训：曾为救浅色主体从 0.15 一路降到 0.05，结果 bike 轮辐间隙的低置信「雾」（raw 0.05~0.15）
     * 全部被判成前景，辐条增粗粘连吞掉楔形区——这是「最原始版本 bike 正常」之后一切异常的根因。
     */
    private const val ALPHA_THRESHOLD = 0.05f

    // ── [修复11] 自适应阈值：局部背景场裁决不自信带 ──
    /**
     * 自信前景阈值：归一化 alpha 高于此值直接判前景，不做颜色裁决。
     * bike 实测：楔形区雾状 raw 集中在 0.05~0.15（场距中位仅 2），车架高光 raw 多在 0.4+，
     * 0.15 分界不碰任何真实主体。
     */
    private const val UNCERTAIN_HIGH = 0.15f
    /**
     * 背景场匹配容差：不自信带像素与局部背景场估计色的最大单通道差。
     * 场距 ≤ 此值 → 判背景（轮辐雾）；> 此值 → 保留前景（秤浅蓝秤面距白底场 40+，安全余量 2 倍）。
     */
    private const val FIELD_MATCH_TOL = 20
    /** 背景场网格单元边长（模型分辨率像素）。32 在 1024 网格下给出 32×32 个单元，兼顾平滑与采样密度 */
    private const val FIELD_CELL = 32

    // ── [修复09] 均匀背景先验参数（P0）──
    /** 背景洪泛的工作分辨率长边上限：控内存与耗时，512 足够刻画背景连通性 */
    private const val BG_WORK_MAX_SIDE = 512
    /** 边框采样带宽度占短边比例，实际 clamp 到 3..12px */
    private const val BG_BORDER_SAMPLE_RATIO = 0.03f
    /** 与背景基准色的最大单通道色差（0-255）。24 可容 JPEG 噪声，又能与主体（秤面与白底差 ≈34）拉开 */
    private const val BG_COLOR_TOLERANCE = 24
    /**
     * 边框模态簇覆盖率下限。[修复11] 起配合「粗直方图模态簇」语义使用；
     * [修复17] 0.60 → 0.45：bike 实测双色背景（暖墙 71% + 白地板 29%）在 createScaledBitmap
     * bilinear 的边框重采样下 coverage 实测仅 ~59-62%，卡在 0.60 边缘导致 bgRef=null，
     * P0/口袋清理/填洞颜色豁免全部跳过，楔形区被 fillHoles 部分填成前景（辐条间桃色残留根因）。
     * 0.45 下 bike 稳过（余量 ~15%）；复杂实拍背景覆盖率通常 < 30%，仍会被拒绝。
     */
    private const val BG_UNIFORM_RATIO = 0.45f
    /** 掩码被清除的比例上限，超过即判定误吞主体（白叠白极端场景），整体回滚 */
    private const val BG_MAX_REMOVE_RATIO = 0.55f

    // ── [修复10] 边缘硬边化参数（P3）──
    /** 上采样回原尺寸后二次二值化的等值线（0-255）。128 = 50%，几何上最接近原二值轮廓 */
    private const val REBINARY_THRESHOLD = 128
    /** 掩码边缘内缩轮数（模型 1024 分辨率，1 轮 = 原图 1 个模型像素）：削掉最外一圈主体/背景混色像素 */
    private const val EDGE_SHRINK_ITER = 1

    // ── [修复09] 洪泛填洞参数（P2）──
    /** 可填补的洞面积上限占前景面积的比例；大镂空（甜甜圈孔、车架三角、轮辐楔形区）保持透明 */
    private const val HOLE_MAX_AREA_RATIO = 0.005f
    /**
     * 可填补的洞面积绝对上限（px，模型 1024 分辨率）。
     * 对齐原「四轮闭运算」的实际填孔能力（3×3 核 ×4 轮 ≈ 8px 宽 ≲ 200px 面积），放宽到 1024 已留足余量。
     * 没有这个上限时，bike 轮辐间的独立楔形区（单块约 1-3k px）会被逐块误填，桃色背景透出。
     */
    private const val HOLE_MAX_AREA_ABS = 1024

    // ── [修复10-追加·v2] 看穿区种子生长清理参数 ──
    /**
     * 种子资格：封闭透明连通域的最小面积（模型 1024 分辨率）。
     * 只有「模型确认为背景且被主体包围」的透明区（bike 已透明的轮辐间隙）才有资格当种子。
     */
    private const val POCKET_SEED_MIN_AREA = 128
    /**
     * 种子资格：种子中位色与背景基准色的最大距离。
     * 排除「被误透明的主体内部暗隙」（颜色与背景差得远）充当种子。
     */
    private const val POCKET_SEED_MAX_DIST = 48
    /**
     * [修复18] fillHoles 颜色豁免容差（洞中位色距基准色 ≤ 此值 = 看穿洞，不填）。
     * 独立于 POCKET_SEED_MAX_DIST：bike 后轮下方楔形区背景渐变更深（实测距 bgRef 达 52），
     * 48 豁免不到导致楔形被重新填回桃色。豁免 = 不填 = 保持透明，放宽是保守方向。
     */
    private const val HOLE_EXEMPT_MAX_DIST = 60
    /**
     * 锚定容差：候选像素与最近种子中位色的最大距离。
     * 实测 bike：楔形区 80% 像素距种子中位色 ≤12；车架暖色高光距 ~14，被该阈值挡住（这是纯颜色方案下
     * 两者的真实分界，再放宽就会吃掉车架高光）。
     */
    private const val POCKET_ANCHOR_TOL = 12
    /** 链式生长的局部步进容差：候选像素与当前像素的最大通道差，用于沿背景渐变爬坡（轮底比种子处颜色更深） */
    private const val POCKET_CHAIN_LOCAL_DELTA = 4
    /** 链式生长的硬上限：无论爬多远，与种子中位色距离不得超过此值，防漂移进同色的车架高光 */
    private const val POCKET_CHAIN_MAX_DIST = 28
    /** 护栏：累计清除像素超过前景面积此比例时整体回滚（种子误判时的最后防线） */
    private const val POCKET_MAX_CLEAR_RATIO = 0.30f

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

            // 5. 后处理：α 硬边 → 缩回原图尺寸 → 套到原图全分辨率 RGB 上
            // [临时调试] 传入调试目录，落盘管线中间产物，定位 bike 轮辐残留后移除
            val cutout = postprocessAlpha(
                rawAlpha, src, src.width, src.height, MODEL_SIZE, MODEL_SIZE,
                context.getExternalFilesDir("cutout_debug")
            )

            // 6. 贴纸化：白边 + 边缘阴影
            val density = context.resources.displayMetrics.density
            val sticker = applyStickerEffect(cutout, density)

            // [临时调试] 把 cutout（贴纸前）和 sticker（贴纸后）都落盘到 cutout_debug，
            // 分离定位 alpha 污染阶段。dump8 实证 4_cutout 楔形区 97% 透明，验证 fix15 生效。
            context.getExternalFilesDir("cutout_debug")?.let { dir ->
                try {
                    dir.mkdirs()
                    java.io.FileOutputStream(java.io.File(dir, "4_cutout.png")).use { out ->
                        cutout.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    java.io.FileOutputStream(java.io.File(dir, "3_final_sticker.png")).use { out ->
                        sticker.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                } catch (_: Exception) { }
            }

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
     * 最终流程：
     * 1. min-max 归一化到 [0,1]
     * 2. letterbox 坐标系构建颜色数组（[修复11]，与掩码对齐）
     * 3. 自适应二值化（[修复11]）：v > 0.15 直接前景；0.05~0.15 不自信带按「局部背景场色距」裁决
     *    （贴近场 → 背景，远离 → 前景），根治 bike 轮辐雾状低置信带被误判为前景的问题
     * 4. 开运算去孤立噪点 → [修复09] P0 背景先验清边 → 看穿区种子生长（兜底）→ 洪泛填洞
     * 5. 保留最大连通域 → [修复10] 边缘内缩一圈
     * 6. 仅缩放 alpha 掩码回原图尺寸，用原图全分辨率 RGB 合成 + [修复10] 二次二值化
     */
    private fun postprocessAlpha(
        rawAlpha: FloatArray,
        src: Bitmap,
        origW: Int,
        origH: Int,
        modelW: Int,
        modelH: Int,
        debugDir: java.io.File? = null
    ): Bitmap {
        val totalPixels = modelW * modelH

        // 0. 一次性加载原图像素（[修复12] 灰填充需要原图分辨率的 RGB 通道，改 origPixels 而非另开数组）
        val origPixels = IntArray(origW * origH)
        src.getPixels(origPixels, 0, origW, 0, 0, origW, origH)

        // 1. Min-max 归一化到 [0, 1]
        var min = Float.MAX_VALUE
        var max = Float.MIN_VALUE
        for (v in rawAlpha) {
            if (v < min) min = v
            if (v > max) max = v
        }
        val range = if (max > min) (max - min) else 1f

        // 2. 颜色数组按 letterbox 映射构建（与掩码同一坐标系）。
        // [修复11] 旧实现 createScaledBitmap(src, 1024, 1024) 是拉伸映射，与掩码的 letterbox 坐标
        // 错位（bike 底部偏差 ~78px），此前一切按掩码坐标查颜色的逻辑实际查的都是错位颜色
        val fitRatio = minOf(modelW.toFloat() / origW, modelH.toFloat() / origH)
        val fitLeft = (modelW - (origW * fitRatio).toInt()) / 2
        val fitTop = (modelH - (origH * fitRatio).toInt()) / 2
        val actW = (origW * fitRatio).toInt().coerceIn(1, modelW)
        val actH = (origH * fitRatio).toInt().coerceIn(1, modelH)
        val scaledContent = Bitmap.createScaledBitmap(src, actW, actH, true)
        val modelColors = IntArray(totalPixels)
        java.util.Arrays.fill(modelColors, 0xFF404040.toInt())
        val contentPixels = IntArray(actW * actH)
        scaledContent.getPixels(contentPixels, 0, actW, 0, 0, actW, actH)
        for (y in 0 until actH) {
            System.arraycopy(contentPixels, y * actW, modelColors, (y + fitTop) * modelW + fitLeft, actW)
        }
        if (scaledContent !== src) scaledContent.recycle()

        // 3. 两段式二值化：
        //    3a. 先按低阈值粗分，取「透明且连通到图像边界」的区域当背景场样本（模型清除的外部背景，最可信）
        //    3b. 再自适应裁决：v > UNCERTAIN_HIGH 直接前景；0.05 < v <= UNCERTAIN_HIGH 的不自信带，
        //        颜色贴近局部背景场（场距 <= FIELD_MATCH_TOL）判背景，远离则保留前景。
        // [修复11 根因] bike 轮辐间隙的模型输出是 raw 0.05~0.15 的雾状低置信带（肉眼看近黑），
        // 单一低阈值 0.05 把整片雾判成前景，辐条增粗粘连吞掉楔形区；0.15 时代雾全透明所以「最原始正常」。
        // 浅色主体细节（秤面距白底场 40+）远离场，不受裁决影响。
        val normAlpha = ByteArray(totalPixels)
        // [临时调试] 同步保存归一化 raw alpha（0-255 灰度），这是判断「楔形区模型置信度」的关键证据
        val rawNormGray = ByteArray(totalPixels)
        for (i in rawAlpha.indices) {
            val v = (rawAlpha[i] - min) / range
            val g = (v.coerceIn(0f, 1f) * 255f).toInt()
            rawNormGray[i] = g.toByte()
            normAlpha[i] = if (v > ALPHA_THRESHOLD) 0xFF.toByte() else 0
        }
        val field = buildBackgroundField(normAlpha, modelColors, modelW, modelH)
        val highT = UNCERTAIN_HIGH
        if (field != null) {
            val gw = (modelW + FIELD_CELL - 1) / FIELD_CELL
            val gh = (modelH + FIELD_CELL - 1) / FIELD_CELL
            for (y in 0 until modelH) {
                val fy = (y + 0.5f) / FIELD_CELL - 0.5f
                val gy0 = fy.toInt().coerceIn(0, gh - 1)
                val gy1 = (gy0 + 1).coerceIn(0, gh - 1)
                val wy = (fy - gy0).coerceIn(0f, 1f)
                val rowBase = y * modelW
                for (x in 0 until modelW) {
                    val i = rowBase + x
                    if (normAlpha[i] != 0xFF.toByte()) continue
                    val v = (rawAlpha[i] - min) / range
                    if (v > highT) continue
                    val fx = (x + 0.5f) / FIELD_CELL - 0.5f
                    val gx0 = fx.toInt().coerceIn(0, gw - 1)
                    val gx1 = (gx0 + 1).coerceIn(0, gw - 1)
                    val wx = (fx - gx0).coerceIn(0f, 1f)
                    val c = modelColors[i]
                    val cr = (c ushr 16) and 0xFF
                    val cg = (c ushr 8) and 0xFF
                    val cb = c and 0xFF
                    // 双线性插值出该像素处的背景场估计色
                    val f00 = field[gy0 * gw + gx0]; val f01 = field[gy0 * gw + gx1]
                    val f10 = field[gy1 * gw + gx0]; val f11 = field[gy1 * gw + gx1]
                    val er = ((f00 ushr 16 and 0xFF) * (1 - wx) * (1 - wy) + (f01 ushr 16 and 0xFF) * wx * (1 - wy) +
                        (f10 ushr 16 and 0xFF) * (1 - wx) * wy + (f11 ushr 16 and 0xFF) * wx * wy)
                    val eg = ((f00 ushr 8 and 0xFF) * (1 - wx) * (1 - wy) + (f01 ushr 8 and 0xFF) * wx * (1 - wy) +
                        (f10 ushr 8 and 0xFF) * (1 - wx) * wy + (f11 ushr 8 and 0xFF) * wx * wy)
                    val eb = ((f00 and 0xFF) * (1 - wx) * (1 - wy) + (f01 and 0xFF) * wx * (1 - wy) +
                        (f10 and 0xFF) * (1 - wx) * wy + (f11 and 0xFF) * wx * wy)
                    val d = maxOf(maxOf(Math.abs(cr - er), Math.abs(cg - eg)), Math.abs(cb - eb))
                    if (d <= FIELD_MATCH_TOL) normAlpha[i] = 0
                }
            }
        }

        // 3. 形态学处理：一轮开运算去孤立噪点。
        // [修复09-P2] 原「四轮闭运算」会把白底误增的须状区桥接焊成实心块，改由 fillHoles 洪泛填洞替代
        morphologicalOpen(normAlpha, modelW, modelH)

        // 3.5 [修复09-P0] 均匀背景先验：清掉「与背景同色且连通到图像边界」的掩码像素。
        // 浅色主体 + 纯色背景时，模型会在真实轮廓外留一圈低置信带，被形态学固化、再被白边膨胀放大成白色凸块。
        // [修复11] bgRef 检测改多色调鲁棒（模态簇），bike 的「墙+地板」双色背景不再误判为不均匀；
        // 颜色数组复用上方 letterbox 版 modelColors（旧拉伸映射已废）。
        val bgRef = detectBackgroundReference(src)
        if (bgRef != null) {
            removeBackgroundBleed(normAlpha, modelW, modelH, src, fitRatio, fitLeft, fitTop, bgRef)

            // 3.5.5 [修复10-追加·v2] 看穿区种子生长清理（兜底）：
            // 自适应阈值后楔形区雾已清，此处仅清理残余的零星同色误判块，正常情况贡献极小
            clearEnclosedBackgroundPockets(normAlpha, modelW, modelH, modelColors, bgRef)

            // 3.6 [修复09-P2] 洪泛填洞（颜色豁免版）：主体内部封闭的「非背景色」透明区补为前景
            fillHoles(normAlpha, modelW, modelH, modelColors, bgRef)
        } else {
            // 3.6 [修复09-P2] 背景不均匀（复杂实拍场景）时退化为纯面积判定
            fillHoles(normAlpha, modelW, modelH, null, null)
        }

        // 4. 只保留最大连通域，去掉背景误识别的小孤岛（如自行车顶部多余块）
        keepLargestConnectedComponent(normAlpha, modelW, modelH)

        // 4.5 [修复10-P3-B] 边缘内缩一圈（十字核，模型分辨率）：
        // 削掉最外一层「主体/背景混色」的抗锯齿像素，使顶层前景不带浅色镶边，白边落在真实背景一侧
        morphologicalErodeCross(normAlpha, modelW, modelH)

        // [修复13 已撤回] fillEnclosedHolesWithGray（修复12）把楔形区填 #8C8C8C 破坏"透明看穿"语义，
        // 用户以参考图（纯透明 + 中性底色）对比后要求撤回。楔形区保持真透明，
        // 由修复11-UI 的 CutoutContainerBg 中性底色兜底透出。

        // [临时调试] 落盘管线中间产物：归一化 raw alpha（判断楔形区模型置信度的关键证据）+ 最终掩码 + 参数。
        // 路径：/sdcard/Android/data/com.litiangu.wuji/files/cutout_debug/，bike 定位完成后移除
        if (debugDir != null) {
            try {
                debugDir.mkdirs()
                fun dumpPng(name: String, bytes: ByteArray) {
                    val px = IntArray(totalPixels) { i ->
                        val g = bytes[i].toInt() and 0xFF
                        (0xFF shl 24) or (g shl 16) or (g shl 8) or g
                    }
                    val bm = Bitmap.createBitmap(px, modelW, modelH, Bitmap.Config.ARGB_8888)
                    java.io.FileOutputStream(java.io.File(debugDir, name)).use { out ->
                        bm.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    bm.recycle()
                }
                dumpPng("1_raw_norm.png", rawNormGray)
                dumpPng("2_final_mask.png", normAlpha)
                java.io.File(debugDir, "info.txt").writeText(
                    "orig=${origW}x$origH model=${modelW}x$modelH min=$min max=$max range=$range " +
                        "threshold=$ALPHA_THRESHOLD bgRef=${bgRef?.toUInt()?.toString(16)}\n"
                )
            } catch (_: Exception) {
                // 调试落盘失败不影响主流程
            }
        }

        // 5. 直接用 normAlpha 数组 nearest-neighbor 采样到原图尺寸，跳过 Bitmap 缩放链路
        // [修复15 根因] dump6 实证：2_final_mask 楔形区 alpha=0，但 3_final_sticker.png 同位置 alpha=255。
        // 链路里两处把 0 像素变成 255：
        //   (a) Bitmap.createBitmap(int[], w, h, ARGB_8888) 对 alpha=0 RGB=0xFFFFFF (or any non-zero) 的
        //       像素在该设备上处理错误——把 non-premul ARGB(0, R, G, B) 当 premul 存储，unpremultiply
        //       时除以 0 设为默认 255，导致楔形区 alpha 错误变成 255。
        //   (b) createScaledBitmap bilinear 在 0/255 边界产生 alpha 中间值，REBINARY(>=128) 全转不透明。
        // 修复：完全绕开 Bitmap 缩放/合成链路，直接遍历 orig 像素 nearest-neighbor 读 normAlpha 数组；
        //       合成时 alpha=0 像素的 RGB 强制写 0x000000（避免 RGB 非零引发 premul 处理错误）。
        val ratio = minOf(modelW.toFloat() / origW, modelH.toFloat() / origH)
        val actualW = (origW * ratio).toInt().coerceAtLeast(1)
        val actualH = (origH * ratio).toInt().coerceAtLeast(1)
        val cropLeft = (modelW - actualW) / 2
        val cropTop = (modelH - actualH) / 2
        val resultPixels = IntArray(origW * origH)
        for (y in 0 until origH) {
            val mY = (y * ratio).toInt() + cropTop
            val origRow = y * origW
            for (x in 0 until origW) {
                val mX = (x * ratio).toInt() + cropLeft
                if (normAlpha[mY * modelW + mX].toInt() != 0) {
                    resultPixels[origRow + x] = (0xFF shl 24) or (origPixels[origRow + x] and 0x00FFFFFF)
                } else {
                    // alpha=0：RGB 强制写 0x000000，避免 Bitmap.createBitmap 对 (0, R, G, B) 的处理错误
                    resultPixels[origRow + x] = 0
                }
            }
        }
        val result = Bitmap.createBitmap(resultPixels, origW, origH, Bitmap.Config.ARGB_8888)
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

    /** 对 byte alpha 掩码做形态学闭运算（先膨胀后腐蚀，填补主体内部小洞） */
    private fun morphologicalClose(alpha: ByteArray, w: Int, h: Int) {
        // 3×3 十字核膨胀
        val dilated = ByteArray(alpha.size)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                var maxVal = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val v = alpha[(y + dy) * w + (x + dx)].toInt() and 0xFF
                        if (v > maxVal) maxVal = v
                    }
                }
                dilated[idx] = maxVal.toByte()
            }
        }
        // 3×3 十字核腐蚀
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                var minVal = 0xFF
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val v = dilated[(y + dy) * w + (x + dx)].toInt() and 0xFF
                        if (v < minVal) minVal = v
                    }
                }
                alpha[idx] = minVal.toByte()
            }
        }
    }

    /**
     * [修复10-P3-B] 十字核腐蚀（4 邻域取最小）。
     *
     * 目的：削掉掩码最外一圈「主体与背景混色」的抗锯齿像素。这些像素在硬边化后成为紧贴白边内侧的浅色镶边，
     * 视觉上等于白边向内增粗；内缩一圈后白边落在真实背景一侧，向内渗白消失。
     *
     * 代价：轮廓整体内缩 `rounds` 个模型像素（1024 分辨率，1px ≈ 0.1% 尺寸，肉眼不可见）；
     * 1px 宽的细结构会被整条抹掉（与旧「四轮闭运算」的等效行为一致）。
     */
    private fun morphologicalErodeCross(alpha: ByteArray, w: Int, h: Int, rounds: Int = EDGE_SHRINK_ITER) {
        var current = alpha
        repeat(rounds) {
            val next = ByteArray(current.size)
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val idx = y * w + x
                    var minVal = current[idx].toInt() and 0xFF
                    val up = current[idx - w].toInt() and 0xFF
                    val down = current[idx + w].toInt() and 0xFF
                    val left = current[idx - 1].toInt() and 0xFF
                    val right = current[idx + 1].toInt() and 0xFF
                    if (up < minVal) minVal = up
                    if (down < minVal) minVal = down
                    if (left < minVal) minVal = left
                    if (right < minVal) minVal = right
                    next[idx] = minVal.toByte()
                }
            }
            current = next
        }
        if (current !== alpha) current.copyInto(alpha)
    }

    /** 只保留最大连通域，清除所有小孤岛（背景误识别或前景碎片） */
    private fun keepLargestConnectedComponent(alpha: ByteArray, w: Int, h: Int) {
        val visited = BooleanArray(alpha.size)
        val label = IntArray(alpha.size) { -1 }
        val areas = mutableListOf<Int>()
        val queue = ArrayDeque<Pair<Int, Int>>()

        for (startY in 0 until h) {
            for (startX in 0 until w) {
                val startIdx = startY * w + startX
                if ((alpha[startIdx].toInt() and 0xFF) == 0 || visited[startIdx]) continue

                var area = 0
                queue.addLast(startX to startY)
                visited[startIdx] = true

                while (queue.isNotEmpty()) {
                    val (x, y) = queue.removeFirst()
                    val idx = y * w + x
                    label[idx] = areas.size
                    area++

                    if (x > 0) {
                        val nIdx = idx - 1
                        if (!visited[nIdx] && (alpha[nIdx].toInt() and 0xFF) > 0) {
                            visited[nIdx] = true
                            queue.addLast((x - 1) to y)
                        }
                    }
                    if (x < w - 1) {
                        val nIdx = idx + 1
                        if (!visited[nIdx] && (alpha[nIdx].toInt() and 0xFF) > 0) {
                            visited[nIdx] = true
                            queue.addLast((x + 1) to y)
                        }
                    }
                    if (y > 0) {
                        val nIdx = idx - w
                        if (!visited[nIdx] && (alpha[nIdx].toInt() and 0xFF) > 0) {
                            visited[nIdx] = true
                            queue.addLast(x to (y - 1))
                        }
                    }
                    if (y < h - 1) {
                        val nIdx = idx + w
                        if (!visited[nIdx] && (alpha[nIdx].toInt() and 0xFF) > 0) {
                            visited[nIdx] = true
                            queue.addLast(x to (y + 1))
                        }
                    }
                }
                areas.add(area)
            }
        }

        if (areas.size <= 1) return
        val keepLabel = areas.indices.maxByOrNull { areas[it] } ?: return
        for (i in alpha.indices) {
            if (label[i] != keepLabel) alpha[i] = 0
        }
    }


    // ══════════════════════════════════════════════════════════
    //  [修复09] P0 均匀背景先验 + P2 洪泛填洞
    // ══════════════════════════════════════════════════════════

    /**
     * [修复11] 构建局部背景场：网格化估计「每个位置的背景颜色」。
     *
     * 样本 = 低阈值掩码中「透明且连通到图像边界」的像素（模型确定的外部背景，最可信；
     * 封闭透明区可能是看穿洞，不入样）。空单元（被主体遮挡）由 BFS 从邻单元最近值传播填充。
     *
     * 动机：bike 这类空间渐变背景（边框 230,210,199 → 轮内 241,192,166）下，单一基准色
     * 无法回答「这个像素像不像它所在位置的背景」；场把判定从全局色距升级为局部色距。
     * 离线实测（bike 真机 dump）：轮辐雾距场中位仅 2（p90=14），车架高光距场 ≥36，完美分离。
     *
     * @return 网格颜色数组（打包 ARGB，gw×gh），无样本时返回 null
     */
    private fun buildBackgroundField(
        alpha: ByteArray, colors: IntArray, w: Int, h: Int
    ): IntArray? {
        // 1. 从四边对透明像素洪泛，标记外部背景（真连通，防止封闭看穿洞的异色像素混入样本）
        val total = w * h
        val external = BooleanArray(total)
        val floodQueue = IntArray(total)
        var fHead = 0; var fTail = 0
        fun seedFlood(idx: Int) {
            if (external[idx] || alpha[idx] != 0.toByte()) return
            external[idx] = true
            floodQueue[fTail++] = idx
        }
        for (x in 0 until w) { seedFlood(x); seedFlood((h - 1) * w + x) }
        for (y in 0 until h) { seedFlood(y * w); seedFlood(y * w + w - 1) }
        while (fHead < fTail) {
            val idx = floodQueue[fHead++]
            val x = idx % w
            val y = idx / w
            if (x > 0) seedFlood(idx - 1)
            if (x < w - 1) seedFlood(idx + 1)
            if (y > 0) seedFlood(idx - w)
            if (y < h - 1) seedFlood(idx + w)
        }

        // 2. 网格累加外部背景样本
        val gw = (w + FIELD_CELL - 1) / FIELD_CELL
        val gh = (h + FIELD_CELL - 1) / FIELD_CELL
        val sumR = LongArray(gw * gh); val sumG = LongArray(gw * gh); val sumB = LongArray(gw * gh)
        val cnt = IntArray(gw * gh)
        for (i in 0 until total) {
            if (!external[i]) continue
            val c = colors[i]
            val gi = (i / w) / FIELD_CELL * gw + (i % w) / FIELD_CELL
            sumR[gi] += (c ushr 16) and 0xFF
            sumG[gi] += (c ushr 8) and 0xFF
            sumB[gi] += c and 0xFF
            cnt[gi]++
        }
        val grid = IntArray(gw * gh)
        val has = BooleanArray(gw * gh)
        val queue = IntArray(gw * gh)
        var head = 0; var tail = 0
        for (gi in 0 until gw * gh) {
            if (cnt[gi] == 0) continue
            grid[gi] = (((sumR[gi] / cnt[gi]).toInt() and 0xFF) shl 16) or
                (((sumG[gi] / cnt[gi]).toInt() and 0xFF) shl 8) or ((sumB[gi] / cnt[gi]).toInt() and 0xFF)
            has[gi] = true
            queue[tail++] = gi
        }
        if (tail == 0) return null
        while (head < tail) {
            val gi = queue[head++]
            val gx = gi % gw
            val gy = gi / gw
            for (dir in 0 until 4) {
                val nx = when (dir) { 0 -> gx - 1; 1 -> gx + 1; else -> gx }
                val ny = when (dir) { 2 -> gy - 1; 3 -> gy + 1; else -> gy }
                if (nx < 0 || nx >= gw || ny < 0 || ny >= gh) continue
                val ni = ny * gw + nx
                if (has[ni]) continue
                grid[ni] = grid[gi]
                has[ni] = true
                queue[tail++] = ni
            }
        }
        return grid
    }

    /**
     * [修复09-P0/修复10-追加] 判定原图背景并取基准色（[修复11] 起为多色调鲁棒版）。
     *
     * 流程：缩到工作分辨率 → 边框带采样 → 16 级粗直方图取模态簇 → 簇内中位数作基准色 → 覆盖率判定。
     *
     * @return 0xFFRRGGBB 背景基准色；null 表示背景复杂（模态簇覆盖率过低），调用方跳过全部背景色相关处理
     */
    private fun detectBackgroundReference(src: Bitmap): Int? {
        val origW = src.width
        val origH = src.height

        // 1. 工作分辨率：长边 ≤ BG_WORK_MAX_SIDE（控内存与耗时）
        val workScale = minOf(
            BG_WORK_MAX_SIDE.toFloat() / origW,
            BG_WORK_MAX_SIDE.toFloat() / origH,
            1f
        )
        val workW = (origW * workScale).toInt().coerceAtLeast(1)
        val workH = (origH * workScale).toInt().coerceAtLeast(1)
        val workPixels = IntArray(workW * workH)
        if (workScale >= 1f) {
            src.getPixels(workPixels, 0, workW, 0, 0, origW, origH)
        } else {
            val small = Bitmap.createScaledBitmap(src, workW, workH, true)
            small.getPixels(workPixels, 0, workW, 0, 0, workW, workH)
            small.recycle()
        }

        // 2. 边框采样：取通道中位数作背景基准色（中位数比均值更抗「主体入框」干扰）
        val band = (minOf(workW, workH) * BG_BORDER_SAMPLE_RATIO).toInt().coerceIn(3, 12)
        val samples = IntArray(workW * workH)
        var sampleCount = 0
        for (y in 0 until workH) {
            val onRowBand = y < band || y >= workH - band
            for (x in 0 until workW) {
                if ((onRowBand || x < band || x >= workW - band) && sampleCount < samples.size) {
                    samples[sampleCount++] = workPixels[y * workW + x]
                }
            }
        }
        if (sampleCount < 64) return null

        // 3. [修复11] 多色调鲁棒判定：粗直方图取边框模态簇，簇内中位数作基准色。
        // 旧逻辑要求 85% 边框像素落在基准色容差内，bike 的「暖色墙 + 白地板」双色背景
        //（白地板占边框带 ~29%）被误判不均匀 → bgRef = null → 颜色机制全跳过（楔形区残留的主因之一）。
        // 模态簇覆盖率 ≥ BG_UNIFORM_RATIO_NEW 即接受；复杂实拍背景覆盖率低仍返回 null。
        val q = HashMap<Int, Int>(256)
        for (i in 0 until sampleCount) {
            val key = ((samples[i] ushr 20 and 0x0F) shl 8) or
                ((samples[i] ushr 12 and 0x0F) shl 4) or (samples[i] ushr 4 and 0x0F)
            q[key] = (q[key] ?: 0) + 1
        }
        val modeKey = q.maxByOrNull { it.value }?.key ?: return null
        val modeR = ((modeKey shr 8) and 0x0F) * 16 + 8
        val modeG = ((modeKey shr 4) and 0x0F) * 16 + 8
        val modeB = (modeKey and 0x0F) * 16 + 8
        val cluster = IntArray(sampleCount)
        var clusterCount = 0
        for (i in 0 until sampleCount) {
            val c = samples[i]
            if (maxOf(
                    maxOf(Math.abs((c ushr 16 and 0xFF) - modeR), Math.abs((c ushr 8 and 0xFF) - modeG)),
                    Math.abs((c and 0xFF) - modeB)
                ) <= 32
            ) {
                cluster[clusterCount++] = c
            }
        }
        if (clusterCount < 64 || clusterCount.toFloat() / sampleCount < BG_UNIFORM_RATIO) return null
        val medR = medianOfChannel(cluster, clusterCount, 16)
        val medG = medianOfChannel(cluster, clusterCount, 8)
        val medB = medianOfChannel(cluster, clusterCount, 0)
        return (0xFF shl 24) or (medR shl 16) or (medG shl 8) or medB
    }

    /**
     * [修复09-P0] 清掉掩码中「与背景同色且连通到图像边界」的像素。
     *
     * 针对浅色主体 + 纯白底商品图：模型会在真实轮廓外输出一圈低置信 alpha，二值化后成为轮廓外的背景带，
     * 经形态学与白边膨胀放大，即用户看到的白色凸块（weighing machine 右下凸出）。
     *
     * 流程：从四边洪泛确定背景区 → 掩码像素反 letterbox 回原图坐标，命中背景区即清零
     *       → 误伤护栏（清除过多整体回滚）。
     *
     * @param bgRef 背景基准色（[detectBackgroundReference] 返回非 null 时才调用本函数）
     * @return true 表示已清除并保留；false 表示已回滚（护栏触发）
     */
    private fun removeBackgroundBleed(
        alpha: ByteArray, w: Int, h: Int,
        src: Bitmap, fitRatio: Float, fitLeft: Int, fitTop: Int, bgRef: Int
    ): Boolean {
        val origW = src.width
        val origH = src.height
        val refR = (bgRef ushr 16) and 0xFF
        val refG = (bgRef ushr 8) and 0xFF
        val refB = bgRef and 0xFF

        // 1. 工作分辨率：长边 ≤ BG_WORK_MAX_SIDE（控内存与耗时）
        val workScale = minOf(
            BG_WORK_MAX_SIDE.toFloat() / origW,
            BG_WORK_MAX_SIDE.toFloat() / origH,
            1f
        )
        val workW = (origW * workScale).toInt().coerceAtLeast(1)
        val workH = (origH * workScale).toInt().coerceAtLeast(1)
        val workPixels = IntArray(workW * workH)
        if (workScale >= 1f) {
            src.getPixels(workPixels, 0, workW, 0, 0, origW, origH)
        } else {
            val small = Bitmap.createScaledBitmap(src, workW, workH, true)
            small.getPixels(workPixels, 0, workW, 0, 0, workW, workH)
            small.recycle()
        }

        // 2. 从四边 4 邻域洪泛出「确定背景区」（颜色不在容差内即止步，主体不会被穿越）
        val bgMask = BooleanArray(workW * workH)
        val queue = IntArray(workW * workH)
        var head = 0
        var tail = 0
        fun seedBg(idx: Int) {
            if (bgMask[idx]) return
            if (colorDistance(workPixels[idx], refR, refG, refB) > BG_COLOR_TOLERANCE) return
            bgMask[idx] = true
            queue[tail++] = idx
        }
        for (x in 0 until workW) {
            seedBg(x)
            seedBg((workH - 1) * workW + x)
        }
        for (y in 0 until workH) {
            seedBg(y * workW)
            seedBg(y * workW + workW - 1)
        }
        while (head < tail) {
            val idx = queue[head++]
            val x = idx % workW
            val y = idx / workW
            if (x > 0) seedBg(idx - 1)
            if (x < workW - 1) seedBg(idx + 1)
            if (y > 0) seedBg(idx - workW)
            if (y < workH - 1) seedBg(idx + workW)
        }

        // 5. 清掩码：模型坐标 → 原图坐标 → 工作坐标，命中背景区即清零
        val backup = alpha.copyOf()
        var fgBefore = 0
        for (i in alpha.indices) if (alpha[i] != 0.toByte()) fgBefore++
        if (fgBefore == 0) return false

        for (ym in 0 until h) {
            val yo = ((ym - fitTop) / fitRatio).toInt()
            if (yo < 0 || yo >= origH) continue
            val bgRowBase = (yo * workH / origH) * workW
            val rowBase = ym * w
            for (xm in 0 until w) {
                val idx = rowBase + xm
                if (alpha[idx] == 0.toByte()) continue
                val xo = ((xm - fitLeft) / fitRatio).toInt()
                if (xo < 0 || xo >= origW) continue
                if (bgMask[bgRowBase + (xo * workW / origW)]) alpha[idx] = 0
            }
        }

        var fgAfter = 0
        for (i in alpha.indices) if (alpha[i] != 0.toByte()) fgAfter++
        val removedRatio = (fgBefore - fgAfter).toFloat() / fgBefore

        // 6. 误伤护栏：清除比例过高意味着主体被当成背景吞掉（白叠白极端场景），整体回滚
        if (removedRatio > BG_MAX_REMOVE_RATIO) {
            backup.copyInto(alpha)
            return false
        }
        return removedRatio > 0f
    }

    /** 取采样集中某一通道的中位数（shift = 16/8/0 对应 R/G/B） */
    private fun medianOfChannel(samples: IntArray, count: Int, shift: Int): Int {
        val vals = IntArray(count)
        for (i in 0 until count) vals[i] = (samples[i] ushr shift) and 0xFF
        vals.sort()
        return vals[count / 2]
    }

    /** 像素与基准色的单通道最大差（Chebyshev 距离，0-255） */
    private fun colorDistance(argb: Int, r: Int, g: Int, b: Int): Int {
        var d = ((argb ushr 16) and 0xFF) - r
        val dr = if (d < 0) -d else d
        d = ((argb ushr 8) and 0xFF) - g
        val dg = if (d < 0) -d else d
        d = (argb and 0xFF) - b
        val db = if (d < 0) -d else d
        return maxOf(dr, maxOf(dg, db))
    }

    /**
     * [修复09-P2] 洪泛填洞，替代原「四轮闭运算」。
     *
     * 闭运算（膨胀+腐蚀）会桥接并填实轮廓外的误增须状区，同时把凹角填圆；洪泛填洞只补「被主体包围的封闭透明区」，
     * 对轮廓外形零改变。
     *
     * 流程：从四边对透明像素洪泛标记外部背景 → 未可达的透明像素即洞 → 逐连通域统计面积 →
     * 面积不超过阈值（前景面积 × HOLE_MAX_AREA_RATIO，clamp 到 64..HOLE_MAX_AREA_ABS）才填为前景。
     * 双重上限的意义：只补「针孔级」真洞，甜甜圈中孔、车架三角、轮辐楔形区等大尺度镂空保持透明。
     *
     * [修复10-追加] 颜色豁免：洞的像素中位色 ≈ 背景基准色时跳过填充。
     * 这类洞是「看穿洞」（真实露出背景，如轮辐间隙），填充会把背景色焊成不透明色块；
     * 真正该填的是主体内部的针孔（模型噪声），其像素颜色 ≈ 主体色，不受影响。
     *
     * @param colors 与掩码同尺寸的原图颜色数组（模型分辨率），为 null 时退化为纯面积判定
     * @param bgRef 背景基准色，为 null 时退化为纯面积判定
     */
    private fun fillHoles(alpha: ByteArray, w: Int, h: Int, colors: IntArray?, bgRef: Int?) {
        val total = w * h
        var fgArea = 0
        for (i in 0 until total) if (alpha[i] != 0.toByte()) fgArea++
        if (fgArea == 0) return
        val maxHole = (fgArea * HOLE_MAX_AREA_RATIO).toInt().coerceIn(64, HOLE_MAX_AREA_ABS)

        val visited = BooleanArray(total)
        val queue = IntArray(total)
        var head = 0
        var tail = 0
        fun pushTransparent(idx: Int) {
            if (visited[idx]) return
            if (alpha[idx] != 0.toByte()) return
            visited[idx] = true
            queue[tail++] = idx
        }

        // 1. 从四边洪泛，标记与图像边界连通的透明区（外部背景）
        for (x in 0 until w) {
            pushTransparent(x)
            pushTransparent((h - 1) * w + x)
        }
        for (y in 0 until h) {
            pushTransparent(y * w)
            pushTransparent(y * w + w - 1)
        }
        while (head < tail) {
            val idx = queue[head++]
            val x = idx % w
            val y = idx / w
            if (x > 0) pushTransparent(idx - 1)
            if (x < w - 1) pushTransparent(idx + 1)
            if (y > 0) pushTransparent(idx - w)
            if (y < h - 1) pushTransparent(idx + w)
        }

        // 2. 未可达的透明像素 = 洞，逐连通域量面积后决定填不填
        val comp = IntArray(total)
        for (start in 0 until total) {
            if (visited[start] || alpha[start] != 0.toByte()) continue
            var cHead = 0
            var cTail = 0
            visited[start] = true
            comp[cTail++] = start
            while (cHead < cTail) {
                val idx = comp[cHead++]
                val x = idx % w
                val y = idx / w
                if (x > 0) {
                    val n = idx - 1
                    if (!visited[n] && alpha[n] == 0.toByte()) { visited[n] = true; comp[cTail++] = n }
                }
                if (x < w - 1) {
                    val n = idx + 1
                    if (!visited[n] && alpha[n] == 0.toByte()) { visited[n] = true; comp[cTail++] = n }
                }
                if (y > 0) {
                    val n = idx - w
                    if (!visited[n] && alpha[n] == 0.toByte()) { visited[n] = true; comp[cTail++] = n }
                }
                if (y < h - 1) {
                    val n = idx + w
                    if (!visited[n] && alpha[n] == 0.toByte()) { visited[n] = true; comp[cTail++] = n }
                }
            }
            if (cTail <= maxHole) {
                // [修复10-追加] 颜色豁免：洞的中位色 ≈ 背景基准色 → 是「看穿洞」，不填
                if (colors != null && bgRef != null) {
                    val holeColors = IntArray(cTail)
                    for (i in 0 until cTail) holeColors[i] = colors[comp[i]]
                    val hr = medianOfChannel(holeColors, cTail, 16)
                    val hg = medianOfChannel(holeColors, cTail, 8)
                    val hb = medianOfChannel(holeColors, cTail, 0)
                    val holeArgb = (0xFF shl 24) or (hr shl 16) or (hg shl 8) or hb
                    val refR = (bgRef ushr 16) and 0xFF
                    val refG = (bgRef ushr 8) and 0xFF
                    val refB = bgRef and 0xFF
                    // [修复10-追加·v2] 豁免容差对齐 POCKET_SEED_MAX_DIST(48)：口袋生长清掉的楔形碎块
                    // 颜色比基准色深（实测距 bgRef 可达 45），用旧值 24 会被这里重新填回桃色
                    // [修复18] 48 → 60：bike 后轮下方楔形区处背景渐变更深（实测距 bgRef 达 52），
                    // 48 豁免不到被重新填回。豁免 = 不填 = 保持透明，放宽是保守方向无误伤风险
                    if (colorDistance(holeArgb, refR, refG, refB) <= HOLE_EXEMPT_MAX_DIST) continue
                }
                for (i in 0 until cTail) alpha[comp[i]] = 0xFF.toByte()
            }
        }
    }

    /**
     * [修复10-追加·v2] 看穿区种子生长：清掉「与已确认看穿区同色延续」的误判前景。
     *
     * 场景：bike 轮辐间隙。分割模型对被主体包围的背景区判定不稳定，部分间隙透明、部分误判为前景
     * （顶层绘制原图后桃色背景透出），视觉上「部分透明部分原色」。
     *
     * v1（按与背景基准色的全局色距分域）实测失败：背景存在空间渐变（bike 边框 ≈(230,210,199)，
     * 轮内楔形区 ≈(241,192,166)，最大通道差达 20+），容差 16 收不进楔形区；放宽到 40 又会波及
     * 体重秤浅色细节。且车架暖色高光（≈(234,199,181)）与楔形区色距仅 ~15，任何全局色距判定都无法区分。
     *
     * v2 思路：模型已正确透明的封闭间隙（看穿区）是「确凿的背景」，以其颜色为锚：
     *  1. 找出所有封闭透明连通域，面积 ≥ [POCKET_SEED_MIN_AREA] 且中位色距基准色 ≤ [POCKET_SEED_MAX_DIST]
     *    者为种子（bike = 已透明的轮辐间隙）；
     *  2. 从种子多源 BFS 生长进相邻不透明像素，两条准入线（任一满足即清）：
     *     a. 锚定：与最近种子中位色距离 ≤ [POCKET_ANCHOR_TOL]；
     *     b. 链式：与当前像素色差 ≤ [POCKET_CHAIN_LOCAL_DELTA]（沿背景渐变爬坡），
     *        且与最近种子中位色距离 ≤ [POCKET_CHAIN_MAX_DIST]（防漂移进同色的车架高光）；
     *  3. 护栏：累计清除超过前景面积 × [POCKET_MAX_CLEAR_RATIO] 整体回滚。
     *
     * 体重秤：主体实心无封闭透明区 → 无种子 → 整段跳过，零风险。
     * 车架高光保护：高光中位色距种子 ~14 > 锚定 12，且高光核心不满足链式准入，最多在交界处吃进 1-2px 抗锯齿边。
     *
     * @param colors 与掩码同尺寸的原图颜色数组（模型分辨率）
     * @param bgRef 背景基准色（[detectBackgroundReference] 返回非 null 时才调用本函数）
     */
    private fun clearEnclosedBackgroundPockets(alpha: ByteArray, w: Int, h: Int, colors: IntArray, bgRef: Int) {
        val total = w * h
        val refR = (bgRef ushr 16) and 0xFF
        val refG = (bgRef ushr 8) and 0xFF
        val refB = bgRef and 0xFF

        // 1. 从四边洪泛标记外部透明区，剩下的透明连通域即「封闭透明区」
        val external = BooleanArray(total)
        val queue = IntArray(total)
        var head = 0
        var tail = 0
        fun pushExternal(idx: Int) {
            if (external[idx] || alpha[idx] != 0.toByte()) return
            external[idx] = true
            queue[tail++] = idx
        }
        for (x in 0 until w) {
            pushExternal(x); pushExternal((h - 1) * w + x)
        }
        for (y in 0 until h) {
            pushExternal(y * w); pushExternal(y * w + w - 1)
        }
        while (head < tail) {
            val idx = queue[head++]
            val x = idx % w
            val y = idx / w
            if (x > 0) pushExternal(idx - 1)
            if (x < w - 1) pushExternal(idx + 1)
            if (y > 0) pushExternal(idx - w)
            if (y < h - 1) pushExternal(idx + w)
        }

        // 2. 逐封闭透明域做种子资格检查（面积 + 中位色），合格者的中位色收进种子锚点集
        val visited = BooleanArray(total)
        val comp = IntArray(total)
        val seedMedians = ArrayList<Int>()
        for (start in 0 until total) {
            if (visited[start] || external[start] || alpha[start] != 0.toByte()) continue
            var cHead = 0
            var cTail = 0
            visited[start] = true
            comp[cTail++] = start
            while (cHead < cTail) {
                val idx = comp[cHead++]
                val x = idx % w
                val y = idx / w
                if (x > 0 && !visited[idx - 1] && !external[idx - 1] && alpha[idx - 1] == 0.toByte()) {
                    visited[idx - 1] = true; comp[cTail++] = idx - 1
                }
                if (x < w - 1 && !visited[idx + 1] && !external[idx + 1] && alpha[idx + 1] == 0.toByte()) {
                    visited[idx + 1] = true; comp[cTail++] = idx + 1
                }
                if (y > 0 && !visited[idx - w] && !external[idx - w] && alpha[idx - w] == 0.toByte()) {
                    visited[idx - w] = true; comp[cTail++] = idx - w
                }
                if (y < h - 1 && !visited[idx + w] && !external[idx + w] && alpha[idx + w] == 0.toByte()) {
                    visited[idx + w] = true; comp[cTail++] = idx + w
                }
            }
            if (cTail < POCKET_SEED_MIN_AREA) continue
            val domainColors = IntArray(cTail)
            for (i in 0 until cTail) domainColors[i] = colors[comp[i]]
            val mr = medianOfChannel(domainColors, cTail, 16)
            val mg = medianOfChannel(domainColors, cTail, 8)
            val mb = medianOfChannel(domainColors, cTail, 0)
            if (maxOf(maxOf(Math.abs(mr - refR), Math.abs(mg - refG)), Math.abs(mb - refB)) <= POCKET_SEED_MAX_DIST) {
                seedMedians.add((mr shl 16) or (mg shl 8) or mb)
            }
        }
        if (seedMedians.isEmpty()) return

        // 3. 多源 BFS 生长：种子像素本身入队，向相邻不透明像素扩张
        val fgCountBefore = countForeground(alpha, total)
        val cleared = BooleanArray(total)
        val grow = IntArray(total)
        var gHead = 0
        var gTail = 0
        for (start in 0 until total) {
            if (alpha[start] == 0.toByte() && !external[start]) {
                cleared[start] = true
                grow[gTail++] = start
            }
        }
        val medCount = seedMedians.size
        while (gHead < gTail) {
            val idx = grow[gHead++]
            val c = colors[idx]
            val cr = c ushr 16 and 0xFF
            val cg = c ushr 8 and 0xFF
            val cb = c and 0xFF
            val x = idx % w
            val y = idx / w
            for (dir in 0 until 4) {
                val n = when (dir) {
                    0 -> if (x > 0) idx - 1 else -1
                    1 -> if (x < w - 1) idx + 1 else -1
                    2 -> if (y > 0) idx - w else -1
                    else -> if (y < h - 1) idx + w else -1
                }
                if (n < 0 || cleared[n] || alpha[n] != 0xFF.toByte()) continue
                val nc = colors[n]
                // 与最近种子中位色的最大通道差
                var best = Int.MAX_VALUE
                for (m in 0 until medCount) {
                    val sm = seedMedians[m]
                    val d = maxOf(
                        maxOf(Math.abs((nc ushr 16 and 0xFF) - (sm ushr 16 and 0xFF)), Math.abs((nc ushr 8 and 0xFF) - (sm ushr 8 and 0xFF))),
                        Math.abs((nc and 0xFF) - (sm and 0xFF))
                    )
                    if (d < best) best = d
                    if (best <= POCKET_ANCHOR_TOL) break
                }
                // 与当前像素的最大通道差（链式爬坡用）
                val localD = maxOf(
                    maxOf(Math.abs((nc ushr 16 and 0xFF) - cr), Math.abs((nc ushr 8 and 0xFF) - cg)),
                    Math.abs((nc and 0xFF) - cb)
                )
                if (best <= POCKET_ANCHOR_TOL ||
                    (best <= POCKET_CHAIN_MAX_DIST && localD <= POCKET_CHAIN_LOCAL_DELTA)
                ) {
                    cleared[n] = true
                    grow[gTail++] = n
                }
            }
        }

        // 4. 护栏 + 落笔
        var clearCount = 0
        for (i in 0 until total) if (cleared[i] && alpha[i] != 0.toByte()) clearCount++
        if (clearCount == 0) return
        if (clearCount > fgCountBefore * POCKET_MAX_CLEAR_RATIO) return
        for (i in 0 until total) if (cleared[i]) alpha[i] = 0
    }

    // [修复12 已撤回 2026-09-08] fillEnclosedHolesWithGray（封闭内孔填中性灰）曾把 bike 楔形区填 #8C8C8C，
    // 用户对比参考图（纯透明 + 中性底色）后要求撤回：楔形区保持真透明，由 UI 端 CutoutContainerBg 兜底。

    /** 统计前景像素数（alpha 非 0） */
    private fun countForeground(alpha: ByteArray, total: Int): Int {
        var n = 0
        for (i in 0 until total) if (alpha[i] != 0.toByte()) n++
        return n
    }

    // ══════════════════════════════════════════════════════════
    //  贴纸化：白边 + 边缘阴影（v2 重写）
    // ══════════════════════════════════════════════════════════

    /**
     * 对透明抠图应用贴纸效果：白色模切边 + 边缘投影阴影。
     *
     * v2 改进：
     * - 白边：真形态学膨胀（[修复10] 八边形近似核：十字/对角交替，多轮迭代），边缘清晰且转角不鼓包
     * - 阴影：仅边缘带（dilatedAlpha − originalAlpha 的差集），非全区域
     * - 模糊：Stack Blur（O(n) 近似高斯），替代不可靠的 BlurMaskFilter
     *
     * 合成顺序（从底到顶）：阴影层 → 白边层 → 原始前景
     *
     * 修复03：贴纸化前先把前景垫进四边各加 margin（=白边宽度）的透明工作画布。
     * 原图物体贴近照片底边时，mask 会触到抠图位图的最后一行，直接贴纸化会让触底
     * 区段的白边/阴影没有渲染空间而整段缺失（bike 前轮底部白边断裂的根因）。
     * 四个 pad 同步扣减 margin，最终输出尺寸与不加 margin 时逐像素一致。
     */
    private fun applyStickerEffect(cutout: Bitmap, density: Float): Bitmap {
        val borderWidthPx = (BORDER_WIDTH_DP * density).toInt().coerceAtLeast(2)
        val blurRadiusPx = (SHADOW_BLUR_RADIUS_DP * density).toInt().coerceAtLeast(3)
        val shadowOffX = (SHADOW_OFFSET_X_DP * density).toInt()
        val shadowOffY = (SHADOW_OFFSET_Y_DP * density).toInt()

        // [修复03] 前景先垫进四边加 margin 的透明工作画布，白边永远有地方画
        val margin = borderWidthPx
        val work = Bitmap.createBitmap(cutout.width + 2 * margin, cutout.height + 2 * margin, Bitmap.Config.ARGB_8888)
        Canvas(work).drawBitmap(cutout, margin.toFloat(), margin.toFloat(), null)

        val w = work.width
        val h = work.height

        // 提取 alpha 通道为 byte 数组（高效操作）
        val alphaBytes = extractAlphaBytes(work, w, h)

        // [修复16 根因] 白边/阴影只画「外部边界」：
        // morphologicalDilate 膨胀 borderWidthPx 轮，楔形区（辐条间隙）只有 15-40 像素宽，
        // 左右两侧主体各膨胀 20 像素就完全填满楔形区——用户看到车轮「辐条区间不透明橙/白」。
        // 修复：先 flood fill 从图像边缘找「外部透明区」，白边和阴影只画在 dilated 与外部透明区的交集，
        // 内部透明区（楔形区、镂空）不画白边不画阴影，保持真透明。
        val externalTransparent = findExternalTransparent(alphaBytes, w, h)

        // 1. 形态学膨胀得到「放大版 alpha」（用于生成白边区域）
        val dilatedBytes = morphologicalDilate(alphaBytes, w, h, borderWidthPx)

        // 2. 计算边缘带 alpha（膨胀版 − 原版 = 仅边缘区域），且限制在外部透明区
        val edgeBytes = ByteArray(alphaBytes.size)
        for (i in edgeBytes.indices) {
            val d = dilatedBytes[i].toInt() and 0xFF
            val o = alphaBytes[i].toInt() and 0xFF
            // 边缘带 = 膨胀后多出来的部分 AND 不在内部透明区（楔形区）
            val delta = (d - o).coerceAtLeast(0)
            edgeBytes[i] = if (externalTransparent[i] && o == 0) delta.toByte() else 0
        }

        // 3. 对边缘带做模糊 → 得到柔和阴影形状
        val shadowShape = stackBlur(edgeBytes, w, h, blurRadiusPx)

        // 4. 合成最终贴纸画布（扩展尺寸以容纳阴影偏移；[修复03] pad 扣减 margin，输出尺寸不变）
        val padL = (blurRadiusPx + maxOf(0, shadowOffX) - margin).coerceAtLeast(0)
        val padT = (blurRadiusPx + maxOf(0, shadowOffY) - margin).coerceAtLeast(0)
        val padR = (blurRadiusPx + maxOf(0, -shadowOffX) - margin).coerceAtLeast(0)
        val padB = (blurRadiusPx + maxOf(0, -shadowOffY) - margin).coerceAtLeast(0)
        val resultW = w + padL + padR
        val resultH = h + padT + padB

        val result = Bitmap.createBitmap(resultW, resultH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Layer 1（最底）：阴影（仅边缘带，模糊后填半透明黑）
        drawShadowLayer(canvas, shadowShape, w, h, padL, padT, shadowOffX, shadowOffY)

        // Layer 2（中间）：白边（膨胀区域填白），同样限制在外部透明区
        // [修复16] 只画 dilated > 0 且在外部透明区的像素；楔形区不画白边
        val borderMask = ByteArray(alphaBytes.size)
        for (i in borderMask.indices) {
            val d = dilatedBytes[i].toInt() and 0xFF
            val o = alphaBytes[i].toInt() and 0xFF
            borderMask[i] = if (externalTransparent[i] && o == 0 && d > 0) 0xFF.toByte() else 0
        }
        drawWhiteBorder(canvas, borderMask, w, h, padL, padT)

        // Layer 3（顶层）：原始前景（覆盖白边内部，保留清晰主体）。
        // [修复03] 改画 work（已含 margin），与阴影/白边两层同偏移对齐，绝对位置与修复前一致
        canvas.drawBitmap(work, padL.toFloat(), padT.toFloat(), null)
        work.recycle()

        return result
    }

    /**
     * [修复16] 从图像四边洪泛，找出「外部透明区」（与图像边缘连通的透明像素）。
     *
     * 白边/阴影只应画在主体和外部背景的边界；内部透明区（楔形区、镂空）与图像边缘不连通，
     * 不应被白边覆盖（否则 border 膨胀 borderWidthPx 轮会把窄楔形区完全填白，视觉上「辐条间隙不透明」）。
     *
     * @return BooleanArray，true = 外部透明区（与边缘连通的透明像素）
     */
    private fun findExternalTransparent(alpha: ByteArray, w: Int, h: Int): BooleanArray {
        val total = w * h
        val external = BooleanArray(total)
        val queue = IntArray(total)
        var head = 0
        var tail = 0
        fun seed(idx: Int) {
            if (external[idx] || alpha[idx] != 0.toByte()) return
            external[idx] = true
            queue[tail++] = idx
        }
        for (x in 0 until w) {
            seed(x); seed((h - 1) * w + x)
        }
        for (y in 0 until h) {
            seed(y * w); seed(y * w + w - 1)
        }
        while (head < tail) {
            val idx = queue[head++]
            val x = idx % w
            val y = idx / w
            if (x > 0) seed(idx - 1)
            if (x < w - 1) seed(idx + 1)
            if (y > 0) seed(idx - w)
            if (y < h - 1) seed(idx + w)
        }
        return external
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
     * 形态学膨胀：八边形近似核（偶数轮十字 4 邻域，奇数轮对角 4 邻域）迭代多次。
     * radius 越大，膨胀越明显（白边越粗）。
     *
     * [修复10-P4] 原实现为 3×3 全方块核（Chebyshev 生长）：45° 边与转角处的外扩是直边的 1.41 倍，
     * 圆弧与圆角处白边明显偏厚、转角鼓包。纯十字核（Manhattan）则反过来只有 0.71 倍。
     * 两种核交替即经典的八边形结构元近似：直边与 45° 边的生长速率接近，白边厚度均匀。
     *
     * 中心像素自保留（`next` 初值取 `current`），保证膨胀结果恒为原掩码的超集。
     */
    private fun morphologicalDilate(alpha: ByteArray, w: Int, h: Int, radius: Int): ByteArray {
        var current = alpha
        // 每次迭代膨胀 1px，共 radius 次
        for (iter in 0 until radius) {
            val useDiagonal = (iter % 2 == 1)
            val next = ByteArray(current.size)
            for (y in 1 until h - 1) {
                for (x in 1 until w - 1) {
                    val idx = y * w + x
                    var maxVal = current[idx].toInt() and 0xFF
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            // 十字核只取 4 邻域，对角核只取 4 个角，按迭代奇偶交替
                            val isCross = (dx == 0 || dy == 0)
                            if (useDiagonal == isCross) continue
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
