package com.litiangu.wuji.ui.designsystem

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.litiangu.wuji.ui.theme.WujiSize
import com.litiangu.wuji.ui.theme.WujiSpace

/**
 * [Step 54] 底部系统栏避让高度。悬浮底栏与列表末卡共用这一份数值。
 *
 * ── 为什么需要这个文件 ──────────────────────────────────────
 *
 * 直觉上 `Modifier.navigationBarsPadding()` 就够了，实测在红米 K80 至尊版
 * （25060RK16C / Android 17）上它返回 0，悬浮底栏直接坐在系统手势条上。
 *
 * 原因有两层：
 *
 * 1. targetSdk 36 落在 Android 15+ 强制 edge-to-edge 区间，本App 窗口带有
 *    `EDGE_TO_EDGE_ENFORCED`（系统强制铺满全屏）与 `FIT_INSETS_CONTROLLED`
 *    （声明 App 自己负责 inset，系统不代劳）两个标志位。
 *
 * 2. 该机型的 `navigationBars` InsetsSource 呈**两态行为**：
 *      手势导航（navigation_mode=2）：全局找不到任何 navigationBars source → 取到 0
 *      三键导航（navigation_mode=0）：frame=[0,2619][1280,2772] = 47.1 dp → 正常
 *
 * 换句话说，系统给不给这一项是二元的，没有「稳定返回一个数值」这回事。
 * 因此**不能单独依赖 inset**，必须取「系统值」与「固定下限」的较大者：
 * 系统真给值时自动切换到真实高度（三键），给不了时靠下限兜住（手势）。
 *
 * ── 复测命令（换机型或改导航模式后验证） ─────────────────────
 *
 * ```bash
 * ADB="C:/Users/GU/AppData/Local/Android/Sdk/platform-tools/adb.exe"
 * $ADB -s <序列号> shell dumpsys window \
 *   | grep -E "InsetsSource id=[^ ]+ type=" | grep -v "mSource="
 * ```
 *
 * 只认 `InsetsSource id=xx type=navigationBars` 这一种行。dumpsys 里
 * `navigationBars` 这个词还会出现在 `fitTypes` / `mShowingTransientInsetsTypes` /
 * `forciblyShownTypes` 这几处**配置声明**文本中，它们不代表系统真的给了高度，
 * 只 grep 关键词会误判成「有值」。
 */
@Composable
fun bottomBarInsetDp(minGap: Dp = MinBottomGap): Dp {
    val density = LocalDensity.current
    // 系统实测值：手势模式此处的 source 不存在，getBottom 返回 0。
    //
    // 注意：`WindowInsets.navigationBars` 这个属性本身带 @Composable（它要从
    // 快照状态里读当前 inset），所以本函数只能是 @Composable，不能标
    // @ReadOnlyComposable —— 编译器会报
    //「@ReadOnlyComposable can only call other @ReadOnlyComposable composables」。
    val insetPx = WindowInsets.navigationBars.getBottom(density)
    // with(density) 把裸 px 换成 dp；maxOf 保证「有系统值就用系统的，没就用下限」
    return with(density) { maxOf(insetPx.toDp(), minGap) }
}

/**
 * 固定下限。手势导航实测 inset = 0，用 24.dp 让胶囊离开系统手势条热区，
 * 数字太小会显得贴边，太大会与胶囊 68.dp 的体量失衡。
 */
val MinBottomGap: Dp = 24.dp

/**
 * [Step 56] 页面内容底部避让悬浮胶囊底栏所需的留白。
 *
 * 首页列表的 `contentPadding.bottom` 与分析页页脚共用这一个数值，保证两处口径一致。
 * 早先分析页自己抄了一份公式（页脚只有 `vertical = 24.dp`，滚到底会被胶囊压住），
 * 提取到本文件就是为了杜绝这种「同一口径两处实现、日后改动只改一处」的漂移。
 *
 * 构成（从下往上）：系统栏避让 + 胶囊高68dp + 中央 FAB 凸出 16dp + 呼吸 8dp。
 */
@Composable
fun floatingBarBottomAvoidance(): Dp =
    bottomBarInsetDp() + WujiSize.bottomBarHeight +
        WujiSize.bottomBarFabProtrude + WujiSpace.sm