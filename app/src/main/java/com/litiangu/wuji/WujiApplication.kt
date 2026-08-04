package com.litiangu.wuji

import android.app.Application
import com.litiangu.wuji.data.db.WujiDatabase
import com.litiangu.wuji.data.db.WujiDatabaseProvider
import com.litiangu.wuji.data.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WujiApplication : Application() {
    val database: WujiDatabase by lazy { WujiDatabaseProvider.get(this) }

    // 启动就绪：数据库构建完成 且 首启标记已读出后为 true。
    // 为 true 时 INSTANCE 已建，所有后续 WujiDatabaseProvider.get() 直接返回、不进 synchronized，
    // 主线程不再被建库阻塞（这是点 CTA 后表单卡顿的根因）。
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    // 首启标记缓存：预热读出后写入，WujiApp 同步读取，首帧即知新/老用户，无 null 白屏。
    var launchedBefore: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        // 预热：在进程启动期完成「建库 + 读标记」，两者都就绪后才放开首页/引导页。
        // 顺序关键：get() 在 synchronized 内跑完 build() 才返回，保证 INSTANCE 已建。
        val warmup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        warmup.launch {
            WujiDatabaseProvider.get(this@WujiApplication)        // 构建数据库（阻塞至建完）
            launchedBefore =
                SettingsDataStore(this@WujiApplication).hasLaunchedBeforeFlow.first()
            _isReady.value = true
        }
    }
}
