package com.litiangu.wuji.ui.item

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.net.toFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 图片存储工具：将用户选择的图片（来自相册 URI 或相机临时文件）复制到应用私有目录，
 * 返回稳定路径供 ItemEntity.imagePath 持久化。
 *
 * 存储位置：context.filesDir/images/（应用私有，卸载时自动清除）。
 * 文件命名：{时间戳}_{随机数}.jpg（避免冲突）。
 */

/** 应用私有图片存储目录 */
private fun getImageDir(context: Context): File {
    val dir = File(context.filesDir, "images")
    if (!dir.exists()) dir.mkdirs()
    return dir
}

/**
 * 生成唯一文件名：时间戳 + 4 位随机数 + .jpg 后缀。
 * 同一毫秒内多次调用靠随机数区分。
 */
private fun generateImageFileName(): String {
    val timestamp = System.currentTimeMillis()
    val random = (1000..9999).random()
    return "${timestamp}_${random}.jpg"
}

/**
 * 将相册返回的 content URI 复制到应用私有 images 目录。
 * 使用 ContentResolver 读取输入流，兼容各种 Uri scheme（file:// / content://）。
 *
 * @param context 上下文
 * @param uri 相册选择返回的 Uri
 * @return 复制后的本地绝对路径；失败返回 null
 */
fun saveImageFromUri(context: Context, uri: Uri): String? {
    return try {
        val targetFile = File(getImageDir(context), generateImageFileName())
        // 用 ContentResolver 打开输入流（兼容 file:// 和 content://）
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        } ?: return null
        targetFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * 将相机拍照的临时文件复制到应用私有 images 目录。
 * TakePicture 合约直接写入临时文件，此处做一次「搬移到正式目录」。
 *
 * @param context 上下文
 * @param sourceFile 相机拍照的临时文件
 * @return 复制后的本地绝对路径；失败返回 null
 */
fun saveImageFromFile(context: Context, sourceFile: File): String? {
    return try {
        val targetFile = File(getImageDir(context), generateImageFileName())
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
        targetFile.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/**
 * 从本地文件路径解码 Bitmap，用于 Compose Image 组件显示。
 * 返回 null 表示文件不存在或无法解码。
 *
 * @param imagePath ItemEntity.imagePath 存储的绝对路径
 * @return 解码后的 Bitmap；失败返回 null
 */
fun decodeImagePath(imagePath: String?): android.graphics.Bitmap? {
    if (imagePath.isNullOrBlank()) return null
    return try {
        BitmapFactory.decodeFile(imagePath)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
