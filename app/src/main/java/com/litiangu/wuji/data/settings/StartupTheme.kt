package com.litiangu.wuji.data.settings

import android.content.Context

/**
 * 深色三档的启动缓存。
 *
 * DataStore 是唯一事实来源，但它是异步的：`onCreate` 想在 `setContent` 之前把窗口底铺对，
 * 只能同步取值。实测在主线程 `runBlocking` 等 DataStore 首值会把冷启动拖死（splash 卡住十几秒），
 * 所以这里用 SharedPreferences 存一份镜像，写入时与 DataStore 同步更新。
 *
 * 读到的值只影响冷启动第一帧；Compose 起来之后以 DataStore 的 Flow 为准，
 * 因此即使两个存储出现短暂不一致，也会在首帧后自愈。
 */
object StartupTheme {
    private const val PREFS = "wuji_startup"
    private const val KEY_DARK_MODE = "dark_mode"

    fun read(context: Context): DarkModePref {
        val value = context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_DARK_MODE, -1)
        return if (value >= 0) DarkModePref.fromValue(value) else DarkModePref.FOLLOW_SYSTEM
    }

    fun write(context: Context, pref: DarkModePref) {
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_DARK_MODE, pref.value)
            .apply()
    }
}
