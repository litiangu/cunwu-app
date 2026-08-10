package com.litiangu.wuji.ui.settings

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.litiangu.wuji.data.db.CategoryBudgetEntity
import com.litiangu.wuji.data.db.CategoryEntity
import com.litiangu.wuji.data.db.ItemEntity
import com.litiangu.wuji.data.db.TrashEntity
import com.litiangu.wuji.data.db.WujiDatabase
import com.litiangu.wuji.data.settings.BackupFileInfo
import com.litiangu.wuji.data.settings.DarkModePref
import com.litiangu.wuji.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 数据备份（Step 23 v2，按 5.3 完整对齐 + 含图 zip）。
 *
 * 备份目录：context.getExternalFilesDir(null)/wuji_backups（app 私有外部存储，免权限；
 * 随应用更新保留；卸载清除，可用「分享文件」转存云端实现跨卸载）。
 *
 * zip 结构：backup.json（BackupPayload，图片字段存 basename）+ images/<basename>（原图与 cutout）。
 * 导出复制 filesDir/images 下图片进 zip；导入解压回 filesDir/images 并据 File(p).name 重映射绝对路径
 * （兼容旧备份的绝对路径写法）。
 *
 * 覆盖还原：事务内 clearAllTables + 按序写入；图片已落盘，不进事务。
 * 所有读写包在 runCatching 返回 Result，UI 用 Snackbar 回显。
 */

/** 备份文件结构版本。未来加字段只增不改，旧备份靠 ignoreUnknownKeys 兼容。 */
const val BACKUP_SCHEMA_VERSION = 1

/** 备份目录：app 私有外部存储下的 wuji_backups。 */
private fun backupDir(context: Context): File =
    File(context.getExternalFilesDir(null), "wuji_backups")

/** 设置备份块：与 SettingsDataStore 的键一一对应（v2 增 3 项备份相关）。 */
@Serializable
data class BackupSettings(
    val darkMode: Int,
    val privacyMode: Boolean,
    val hapticFeedback: Boolean,
    val budgetAnchor: Double,
    val monthlyBudgetAnchor: Double,
    val viewMode: String,
    val hasLaunchedBefore: Boolean,
    val lastBackupAt: Long = 0L,
    val backupReminderOn: Boolean = true,
    val backupReminderThresholdDays: Int = 14,
)

/** 完整备份载荷：四表 + 设置 + 元信息。图片路径在导出时转为 basename。 */
@Serializable
data class BackupPayload(
    val schemaVersion: Int = BACKUP_SCHEMA_VERSION,
    val exportedAt: Long,
    val items: List<ItemEntity>,
    val categories: List<CategoryEntity>,
    val categoryBudgets: List<CategoryBudgetEntity>,
    val trash: List<TrashEntity>,
    val settings: BackupSettings,
)

/** 导入结果摘要（仅用于 UI 回显，不序列化）。 */
data class BackupImportSummary(
    val items: Int,
    val categories: Int,
    val categoryBudgets: Int,
    val trash: Int,
)

class BackupViewModel(
    private val db: WujiDatabase,
    private val settings: SettingsDataStore,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    // region 导出（创建备份）

    /** 创建备份：建 payload（图片转 basename）→ 写 zip（backup.json + images/）→ 置 lastBackupAt。 */
    suspend fun createBackup(context: Context): Result<BackupFileInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = backupDir(context)
            dir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.CHINA).format(Date())
            val file = File(dir, "wuji-backup-$ts.zip")
            val payload = buildPayload(context)
            val imageDir = File(context.filesDir, "images")
            ZipOutputStream(FileOutputStream(file)).use { zos ->
                zos.putNextEntry(ZipEntry("backup.json"))
                zos.write(json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
                for (name in collectImageBasenames(payload)) {
                    val src = File(imageDir, name)
                    if (src.exists()) {
                        zos.putNextEntry(ZipEntry("images/$name"))
                        src.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            settings.setLastBackupAt(System.currentTimeMillis())
            BackupFileInfo(file.name, file.lastModified(), file.length())
        }
    }

    /** 组装 payload：读四表 + 设置，图片路径转 basename 便于跨设备/重装重映射。 */
    private suspend fun buildPayload(context: Context): BackupPayload {
        val settingsBackup = with(settings) {
            BackupSettings(
                darkMode = darkModeFlow.first().value,
                privacyMode = privacyModeFlow.first(),
                hapticFeedback = hapticFeedbackFlow.first(),
                budgetAnchor = budgetAnchorFlow.first(),
                monthlyBudgetAnchor = monthlyBudgetAnchorFlow.first(),
                viewMode = viewModeFlow.first(),
                hasLaunchedBefore = hasLaunchedBeforeFlow.first(),
                lastBackupAt = lastBackupAtFlow.first(),
                backupReminderOn = backupReminderOnFlow.first(),
                backupReminderThresholdDays = backupReminderThresholdDaysFlow.first(),
            )
        }
        val items = db.itemDao().getAll().map { remapToBackup(it) }
        val trash = db.trashDao().getAll().map { remapTrashToBackup(it) }
        return BackupPayload(
            schemaVersion = BACKUP_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            items = items,
            categories = db.categoryDao().getAll(),
            categoryBudgets = db.categoryBudgetDao().getAll(),
            trash = trash,
            settings = settingsBackup,
        )
    }

    private fun remapToBackup(e: ItemEntity): ItemEntity = e.copy(
        imagePath = e.imagePath?.let { File(it).name },
        croppedImagePath = e.croppedImagePath?.let { File(it).name },
    )

    private fun remapTrashToBackup(e: TrashEntity): TrashEntity = e.copy(
        imagePath = e.imagePath?.let { File(it).name },
        croppedImagePath = e.croppedImagePath?.let { File(it).name },
    )

    /** 收集所有需打包的图片 basename（items + trash 的原图与 cutout）。 */
    private fun collectImageBasenames(payload: BackupPayload): Set<String> {
        val set = mutableSetOf<String>()
        payload.items.forEach {
            it.imagePath?.let { p -> set.add(File(p).name) }
            it.croppedImagePath?.let { p -> set.add(File(p).name) }
        }
        payload.trash.forEach {
            it.imagePath?.let { p -> set.add(File(p).name) }
            it.croppedImagePath?.let { p -> set.add(File(p).name) }
        }
        return set
    }

    // endregion

    // region 列表 / 删除 / 分享 / 保存

    /** 删除本地备份文件。 */
    suspend fun deleteBackup(file: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!file.delete() && file.exists()) throw IOException("删除失败：${file.name}")
        }
    }

    /** 构造分享 Intent（经 FileProvider 授权读），由 UI 调起系统分享面板。 */
    fun shareBackupIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 保存到手机：复制一份到公共 Downloads/wuji（MediaStore，无需存储权限）。 */
    suspend fun saveToPhone(context: Context, file: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/wuji")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: throw IOException("无法在 Downloads 创建文件（uri=null）")
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: throw IOException("无法写入 Downloads（uri=$uri）")
            Unit
        }
    }

    // endregion

    // region 导入（还原）

    /** 从本地备份文件还原。 */
    suspend fun restoreFromFile(file: File, context: Context): Result<BackupImportSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val imageDir = File(context.filesDir, "images")
                imageDir.mkdirs()
                val payload = ZipFile(file).use { zip ->
                    val je = zip.getEntry("backup.json")
                        ?: throw IOException("备份文件缺少 backup.json")
                    val text = zip.getInputStream(je).bufferedReader(StandardCharsets.UTF_8).readText()
                    zip.entries().asSequence().forEach { ze ->
                        if (!ze.isDirectory && ze.name.startsWith("images/")) {
                            val base = ze.name.removePrefix("images/")
                            zip.getInputStream(ze).use { ins ->
                                File(imageDir, base).outputStream().use { ins.copyTo(it) }
                            }
                        }
                    }
                    json.decodeFromString<BackupPayload>(text)
                }
                applyPayload(payload, context)
            }
        }

    /** 从外部（SAF 选中的）zip uri 还原。 */
    suspend fun restoreFromUri(uri: Uri, context: Context): Result<BackupImportSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                val imageDir = File(context.filesDir, "images")
                imageDir.mkdirs()
                val payload = context.contentResolver.openInputStream(uri)?.use { input ->
                    val zis = ZipInputStream(input)
                    val buf = ByteArray(8192)
                    var result: BackupPayload? = null
                    var ze: ZipEntry?
                    while (zis.nextEntry.also { ze = it } != null) {
                        val entry = ze!!
                        if (entry.isDirectory) {
                            zis.closeEntry()
                            continue
                        }
                        if (entry.name == "backup.json") {
                            result = json.decodeFromString(String(zis.readBytes(), StandardCharsets.UTF_8))
                        } else if (entry.name.startsWith("images/")) {
                            val base = entry.name.removePrefix("images/")
                            File(imageDir, base).outputStream().use { os ->
                                var len: Int
                                while (zis.read(buf).also { len = it } != -1) os.write(buf, 0, len)
                            }
                        }
                        zis.closeEntry()
                    }
                    result ?: throw IOException("备份文件缺少 backup.json")
                } ?: throw IOException("无法读取导入文件（uri=$uri）")
                applyPayload(payload, context)
            }
        }

    /** 落库：版本校验 → 事务清空+写入 → 恢复设置。图片已在调用前解压到 filesDir/images。 */
    private suspend fun applyPayload(payload: BackupPayload, context: Context): BackupImportSummary {
        if (payload.schemaVersion > BACKUP_SCHEMA_VERSION) {
            throw IllegalArgumentException(
                "备份文件版本(${payload.schemaVersion})高于当前应用支持($BACKUP_SCHEMA_VERSION)，请升级应用后再导入"
            )
        }
        db.withTransaction {
            db.clearAllTables()
            payload.categories.forEach { db.categoryDao().insert(it) }
            payload.items.map { remapFromBackup(it, context) }.forEach { db.itemDao().insert(it) }
            payload.categoryBudgets.forEach { db.categoryBudgetDao().upsert(it) }
            payload.trash.map { remapTrashFromBackup(it, context) }.forEach { db.trashDao().insert(it) }
        }
        with(settings) {
            setDarkMode(DarkModePref.fromValue(payload.settings.darkMode))
            setPrivacyMode(payload.settings.privacyMode)
            setHapticFeedback(payload.settings.hapticFeedback)
            setBudgetAnchor(payload.settings.budgetAnchor)
            setMonthlyBudgetAnchor(payload.settings.monthlyBudgetAnchor)
            setViewMode(payload.settings.viewMode)
            setHasLaunchedBefore(payload.settings.hasLaunchedBefore)
            setLastBackupAt(payload.settings.lastBackupAt)
            setBackupReminderOn(payload.settings.backupReminderOn)
            setBackupReminderThresholdDays(payload.settings.backupReminderThresholdDays)
        }
        return BackupImportSummary(
            items = payload.items.size,
            categories = payload.categories.size,
            categoryBudgets = payload.categoryBudgets.size,
            trash = payload.trash.size,
        )
    }

    private fun remapFromBackup(e: ItemEntity, context: Context): ItemEntity = e.copy(
        imagePath = absImagePath(e.imagePath, context),
        croppedImagePath = absImagePath(e.croppedImagePath, context),
    )

    private fun remapTrashFromBackup(e: TrashEntity, context: Context): TrashEntity = e.copy(
        imagePath = absImagePath(e.imagePath, context),
        croppedImagePath = absImagePath(e.croppedImagePath, context),
    )

    /** basename → 当前设备 filesDir/images 下的绝对路径；空/空白返回 null。 */
    private fun absImagePath(p: String?, context: Context): String? {
        if (p.isNullOrBlank()) return null
        val base = File(p).name
        return File(context.filesDir, "images/$base").absolutePath
    }

    // endregion
}
