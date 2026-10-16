package com.litiangu.wuji.ui.settings

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.litiangu.wuji.data.settings.UserProfileRepository
import com.litiangu.wuji.ui.designsystem.fireHapticIfEnabled
import com.litiangu.wuji.ui.item.decodeImagePath
import com.litiangu.wuji.ui.item.deleteImagePath
import com.litiangu.wuji.ui.item.saveImageFromFile
import com.litiangu.wuji.ui.item.saveImageFromUri
import com.litiangu.wuji.ui.theme.WujiRadius
import com.litiangu.wuji.ui.theme.WujiSpace
import kotlinx.coroutines.launch
import java.io.File

/**
 * 用户信息编辑子页（Step 25）：昵称 + 头像(图片)。
 *
 * 资料区（设置页 ProfileSection）点击后进入本页。支持：
 * - 昵称：文本输入，保存落 DataStore，设置页实时回显。
 * - 头像：相册/相机选图，圆形裁切预览，保存落本地路径；支持「恢复默认头像」（清空路径）。
 *
 * 数据层复用 SettingsDataStore（userNickname / userAvatarPath）+ ImageUtils 存图到 filesDir/images。
 * 导航沿用 MainActivity 布尔状态机（showProfileEdit），全屏 overlay 叠设置页之上。
 * 不接智能抠图：头像天然圆形裁切，无需透明贴纸化。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 本地编辑态：从应用启动期预热好的内存缓存取当前值，首帧即真实昵称/头像。
    var nickname by remember { mutableStateOf(UserProfileRepository.nickname.value) }
    var avatarPath by remember { mutableStateOf(UserProfileRepository.avatarPath.value) }
    var saving by remember { mutableStateOf(false) }

    // 相机拍照临时文件
    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    // 相册选择：GetContent 返回 content URI → 复制到应用私有目录 → 回填 avatarPath
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = saveImageFromUri(context, uri)
            if (path != null) {
                // 替换旧头像图文件（避免孤儿文件堆积）
                val old = avatarPath
                avatarPath = path
                if (old.isNotEmpty() && old != path) deleteImagePath(old)
            }
        }
    }

    // 相机拍照：TakePicture 写入临时文件 → 复制到应用私有目录 → 回填 avatarPath
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraFile?.exists() == true) {
            val path = saveImageFromFile(context, tempCameraFile!!)
            if (path != null) {
                val old = avatarPath
                avatarPath = path
                if (old.isNotEmpty() && old != path) deleteImagePath(old)
            }
            tempCameraFile?.delete()
            tempCameraFile = null
        } else {
            tempCameraFile?.delete()
            tempCameraFile = null
        }
    }

    val avatarBitmap: Bitmap? = remember(avatarPath) { decodeImagePath(avatarPath) }
    val canSave = nickname.isNotBlank() && !saving

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "编辑资料",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = WujiSpace.sm)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
                Spacer(Modifier.height(WujiSpace.lg))

                // 头像编辑卡
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 1.dp, shape = RoundedCornerShape(WujiRadius.cardLg), clip = false),
                    shape = RoundedCornerShape(WujiRadius.cardLg),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // 头像预览：96dp 圆，有图裁切、无图回退默认图标
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (avatarBitmap != null) {
                                Image(
                                    bitmap = avatarBitmap.asImageBitmap(),
                                    contentDescription = "头像预览",
                                    modifier = Modifier.matchParentSize().clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(64.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(WujiSpace.md))

                        // 拍照 / 相册 / 恢复默认 三按钮横排
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val file = File(context.cacheDir, "temp_avatar_${System.currentTimeMillis()}.jpg")
                                    tempCameraFile = file
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file,
                                    )
                                    cameraLauncher.launch(uri)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(WujiSpace.xs))
                                Text("拍照")
                            }
                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(WujiSpace.xs))
                                Text("相册")
                            }
                            OutlinedButton(
                                onClick = {
                                    // 恢复默认头像：清空路径 + 删旧文件
                                    val old = avatarPath
                                    avatarPath = ""
                                    if (old.isNotEmpty()) deleteImagePath(old)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            ) {
                                Text("默认")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(WujiSpace.md))

                // 昵称编辑卡
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = 1.dp, shape = RoundedCornerShape(WujiRadius.cardLg), clip = false),
                    shape = RoundedCornerShape(WujiRadius.cardLg),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tonalElevation = 0.dp,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(
                            text = "昵称",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(WujiSpace.sm))
                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            placeholder = {
                                Text(
                                    text = "请输入昵称",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(96.dp))
            }

            // 保存按钮：固定底部（用 Box 叠在滚动内容之上，保证始终可见、不重叠内容）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Button(
                    onClick = {
                        if (!saving && nickname.isNotBlank()) {
                            saving = true
                            scope.launch {
                                // 先持久化再返回：避免协程被 onBack 取消导致设置页看不到更新。
                                try {
                                    UserProfileRepository.setNickname(nickname.trim())
                                    UserProfileRepository.setAvatarPath(avatarPath)
                                    context.fireHapticIfEnabled()
                                    onBack()
                                } finally {
                                    saving = false
                                }
                            }
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(WujiRadius.md),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Text(
                        text = if (saving) "保存中..." else "保存",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
    }
}
