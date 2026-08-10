package com.litiangu.wuji.data.settings

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 用户资料内存缓存（Step 25 修复）。
 *
 * 问题：DataStore 读取是异步的，设置页/编辑页首帧会先用默认值渲染，导致
 * 「默认昵称/头像 → 已保存昵称/头像」的闪跳。
 *
 * 解决：应用在后台预热期就把 DataStore 里当前值读进内存 StateFlow；后续
 * UI 首帧即可拿到真实值，无需 loading、无闪跳。写入先更新 DataStore，
 * StateFlow 通过 Flow 订阅自动同步。
 */
object UserProfileRepository {
    private lateinit var settings: SettingsDataStore

    private val _nickname = MutableStateFlow("本地用户")
    val nickname: StateFlow<String> = _nickname

    private val _avatarPath = MutableStateFlow("")
    val avatarPath: StateFlow<String> = _avatarPath

    /**
     * 在 Application 预热协程中调用（isReady 置 true 之前）。
     * 同步读取一次 DataStore 当前值写入 StateFlow，再启动后台订阅。
     */
    suspend fun init(context: Context) {
        if (::settings.isInitialized) return
        val appContext = context.applicationContext
        settings = SettingsDataStore(appContext)

        // 先同步把当前持久值读到内存，保证 UI 首帧正确。
        _nickname.value = settings.userNicknameFlow.first()
        _avatarPath.value = settings.userAvatarPathFlow.first()

        // 后续 DataStore 变化自动同步到 StateFlow。
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch { settings.userNicknameFlow.collect { _nickname.value = it } }
        scope.launch { settings.userAvatarPathFlow.collect { _avatarPath.value = it } }
    }

    suspend fun setNickname(value: String) = settings.setUserNickname(value)

    suspend fun setAvatarPath(value: String) = settings.setUserAvatarPath(value)
}
