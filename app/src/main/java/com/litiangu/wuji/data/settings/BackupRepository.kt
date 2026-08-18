package com.litiangu.wuji.data.settings

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

/**
 * 本地备份列表项（不序列化）。从 ui.settings 迁移到 data.settings，避免 data 层反向依赖 ui 层。
 */
data class BackupFileInfo(
    val name: String,
    val lastModified: Long,
    val size: Long,
)

/**
 * 数据备份页内存缓存（消除进入时的默认闪跳，对齐 Step 25 的 UserProfileRepository / 预算锚点的 BudgetAnchorRepository）。
 *
 * 问题：备份提醒卡（提醒开关 / 阈值 / 上次备份）来自 DataStore 三条流，用
 * `collectAsState(initial=默认值)` 占位，改过设置后默认值 ≠ 真实值，首帧闪一下；
 * 本地备份列表用 `produceState(initialValue=emptyList())`，首帧空列表再填真实项，也闪一下。
 *
 * 解决：应用预热期把当前值同步读进内存 StateFlow，UI 首帧即真实值，无需 loading；
 * 写入仍走 DataStore / 文件，StateFlow 通过订阅自动同步。
 */
object BackupRepository {
    private lateinit var settings: SettingsDataStore
    private lateinit var appContext: Context

    private val _reminderOn = MutableStateFlow(false)
    val reminderOn: StateFlow<Boolean> = _reminderOn

    private val _threshold = MutableStateFlow(14)
    val threshold: StateFlow<Int> = _threshold

    private val _lastBackupAt = MutableStateFlow(0L)
    val lastBackupAt: StateFlow<Long> = _lastBackupAt

    // 本地备份列表：文件系统相关，预热列出一次；创建/删除后由页面调用 refresh 重新列举。
    private val _backups = MutableStateFlow<List<BackupFileInfo>>(emptyList())
    val backups: StateFlow<List<BackupFileInfo>> = _backups

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun init(context: Context) {
        if (::settings.isInitialized) return
        appContext = context.applicationContext
        settings = SettingsDataStore(appContext)

        // 先同步把当前持久值读到内存，保证 UI 首帧正确（与 DataStore 真实值一致，无默认占位闪跳）。
        _reminderOn.value = settings.backupReminderOnFlow.first()
        _threshold.value = settings.backupReminderThresholdDaysFlow.first()
        _lastBackupAt.value = settings.lastBackupAtFlow.first()
        _backups.value = listBackupFiles(appContext)

        // 后续变化自动同步到 StateFlow。
        scope.launch { settings.backupReminderOnFlow.collect { _reminderOn.value = it } }
        scope.launch { settings.backupReminderThresholdDaysFlow.collect { _threshold.value = it } }
        scope.launch { settings.lastBackupAtFlow.collect { _lastBackupAt.value = it } }
    }

    /** 重新列举本地备份文件（创建/删除后调用）。 */
    suspend fun refreshBackups() {
        _backups.value = listBackupFiles(appContext)
    }

    /** 同步列举本地备份 zip（按修改时间倒序），与 BackupViewModel.listBackups 同源。 */
    private fun listBackupFiles(context: Context): List<BackupFileInfo> {
        val dir = File(context.getExternalFilesDir(null), "wuji_backups")
        if (!dir.exists()) return emptyList()
        return dir
            .listFiles { f -> f.extension.equals("zip", ignoreCase = true) }
            ?.map { BackupFileInfo(it.name, it.lastModified(), it.length()) }
            ?.sortedByDescending { it.lastModified }
            ?: emptyList()
    }
}
