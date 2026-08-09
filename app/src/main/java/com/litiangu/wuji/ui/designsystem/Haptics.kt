package com.litiangu.wuji.ui.designsystem

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import com.litiangu.wuji.data.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 全局触感反馈工具。
 *
 * 设计约束（见《设置页面+震动反馈功能.md》）：系统级轻震动，不做自定义波形；
 * 仅关键操作给触感确认，非全量按钮。minSdk=34，VibratorManager / VibrationEffect 全可用。
 */
object Haptics {
    /** 无条件轻震（12ms 单发）。供「已知开启」的调用点（如震动开关自身切换）。 */
    fun light(context: Context) {
        Log.d("HAPTIC", "vibrate 12ms")   // TODO-Step19-verify: 临时调试，验收后删
        val vibrator = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager)
            .defaultVibrator
        vibrator.vibrate(
            VibrationEffect.createOneShot(12L, VibrationEffect.DEFAULT_AMPLITUDE),
        )
    }
}

/**
 * 读震动开关后决定是否轻震。开启才触发，关闭时静默。
 * 调用点无需持有开关状态，避免参数透传链路膨胀。
 * 用 Main.immediate 协程异步读 DataStore（已缓存，延迟可忽略），不阻塞点击。
 */
fun Context.fireHapticIfEnabled() {
    val ctx = this
    CoroutineScope(Dispatchers.Main.immediate).launch {
        val enabled = SettingsDataStore(ctx).hapticFeedbackFlow.first()
        Log.d("HAPTIC", "switch=$enabled")   // TODO-Step19-verify: 临时调试，验收后删
        if (enabled) {
            Haptics.light(ctx)
        }
    }
}
