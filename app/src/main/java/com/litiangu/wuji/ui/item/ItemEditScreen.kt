package com.litiangu.wuji.ui.item

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.litiangu.wuji.data.db.CategoryEntity
import com.litiangu.wuji.data.db.ItemStatus
import com.litiangu.wuji.data.db.WujiDatabaseProvider
import com.litiangu.wuji.ui.category.CustomCategoryDialog
import com.litiangu.wuji.ui.designsystem.categoryDotColor
import com.litiangu.wuji.ui.designsystem.categoryIcon
import com.litiangu.wuji.ui.theme.BrandOrange
import com.litiangu.wuji.ui.theme.BrandOrangeDark
import com.litiangu.wuji.ui.theme.TintOrangeDark
import com.litiangu.wuji.ui.theme.TintOrangeLight
import com.litiangu.wuji.ui.theme.WujiRadius
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 新增 / 编辑物品表单 [Screen 2.1]。
 * itemId == null 为新增，否则编辑该物品（回显）。
 * initialCategoryId：分类选择页预选的分类 id（Step 13 方案 A）。
 * onDone：保存成功或确认放弃后调用，回到首页。
 *
 * Step 14 新增：
 * - 图片上传（Screen 2.5）：顶部占位区点击进入图片选择页（拍照/相册）
 * - 放弃保护：× 按钮与系统返回手势均做 dirty 检查，脏状态弹确认弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemEditScreen(
    itemId: Long?,
    initialCategoryId: Long? = null,   // Step 13 方案 A：预选分类由构造参数注入
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 方案 A：构造参数直接注入预选，无需 LaunchedEffect 副作用
    val viewModel = remember {
        val db = WujiDatabaseProvider.get(context)
        ItemEditViewModel(db.itemDao(), db.categoryDao(), itemId, initialCategoryId)
    }
    val categories by viewModel.categories.collectAsStateWithLifecycle(emptyList())
    // 预置 + 自定义统一排序（与分类选择页一致）：按 sortOrder 升序、其次 id
    val sortedCats = categories.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    val selectedCategory = categories.firstOrNull { it.id == viewModel.categoryId }
    val catName = selectedCategory?.name ?: ""
    val title = buildString {
        append(if (itemId != null) "编辑" else "新增")
        if (catName.isNotEmpty()) append(catName)
        append("物品")
    }

    // 深色判定（不依赖系统设置，按当前 colorScheme 亮度，覆盖跟随系统/强制档）
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tintOrange = if (isDark) TintOrangeDark else TintOrangeLight
    val brand = if (isDark) BrandOrangeDark else BrandOrange

    var showDatePicker by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }

    // Step 14：图片上传 overlay 状态
    var showImageUpload by remember { mutableStateOf(false) }
    // Step 14：放弃保护弹窗状态
    var showDiscardDialog by remember { mutableStateOf(false) }

    // 相机拍照临时文件（TakePicture 合约需要预先创建文件并通过 FileProvider 传 URI）
    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    val dateFormat = remember { SimpleDateFormat("yyyy年M月d日", Locale.CHINA) }

    // ====== Step 14：ActivityResultLaunchers（必须在 Composable 顶层声明）======

    // 相册选择：GetContent 返回 content URI → 复制到应用私有目录 → 回填 viewModel
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val path = saveImageFromUri(context, uri)
            viewModel.onImageChange(path)
        }
        // 选完不关闭上传页：留在上传页预览，用户确认后再点「确认」回表单
    }

    // 相机拍照：TakePicture 写入临时文件 → 复制到应用私有目录 → 回填 viewModel
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraFile?.exists() == true) {
            val path = saveImageFromFile(context, tempCameraFile!!)
            viewModel.onImageChange(path)
            // 清理临时文件
            tempCameraFile?.delete()
            tempCameraFile = null
        } else {
            // 拍照取消或失败：清理可能残留的空文件
            tempCameraFile?.delete()
            tempCameraFile = null
        }
        // 拍完不关闭上传页：留在上传页预览，用户确认后再点「确认」回表单
    }

    // ====== Step 14：放弃保护统一入口 ======
    /** 请求关闭表单：脏状态弹确认弹窗，干净状态直接退出 */
    fun requestClose() {
        if (viewModel.isDirty) {
            showDiscardDialog = true
        } else {
            onDone()
        }
    }

    // 系统返回手势拦截（始终启用；内部根据 isDirty 决定是否弹确认）
    androidx.activity.compose.BackHandler {
        requestClose()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = { requestClose() }) {   // Step 14：走放弃保护
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onDone) }) {
                        Text("保存", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // ====== 表单主体（始终渲染）======
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ===== 顶部图片占位区（Step 14 已接入真实上传流程）=====
                // 按展示态决定占位区显示原图还是抠图贴纸
                val formDisplayImage = if (viewModel.imageDisplayMode == 1 && viewModel.croppedImagePath != null) {
                    viewModel.croppedImagePath
                } else {
                    viewModel.imagePath
                }
                ImagePlaceholder(
                    categoryKey = selectedCategory?.colorKey,
                    imagePath = formDisplayImage,
                    onClick = { showImageUpload = true }
                )

                // ===== 分类选择区 =====
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 行一：自定义 + 清除选择
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AssistChip(
                            onClick = { showCustomDialog = true },
                            label = { Text("+ 自定义") },
                            leadingIcon = {
                                Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = tintOrange,
                                labelColor = brand,
                                leadingIconContentColor = brand
                            )
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { viewModel.onCategorySelect(null) }) {
                            Text("清除选择", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // 行二：备选分类横向滚动（首帧空，AnimatedVisibility 防两步渲染）
                    AnimatedVisibility(visible = sortedCats.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            sortedCats.forEach { cat ->
                                FilterChip(
                                    selected = viewModel.categoryId == cat.id,
                                    onClick = { viewModel.onCategorySelect(cat.id) },
                                    label = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(categoryDotColor(cat.colorKey), CircleShape)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(cat.name)
                                        }
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = tintOrange.copy(alpha = 0.15f),
                                        selectedLabelColor = brand,
                                        selectedLeadingIconColor = brand
                                    )
                                )
                            }
                        }
                    }
                }

                // ===== 表单区：三张独立卡片 =====

                // 卡片 1：物品状态
                FormCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "物品状态",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            ItemStatus.entries.forEach { status ->
                                FilterChip(
                                    selected = viewModel.status == status,
                                    onClick = { viewModel.onStatusSelect(status) },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(statusDotColor(status), CircleShape)
                                        )
                                    },
                                    label = { Text(status.label, style = MaterialTheme.typography.bodySmall) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = tintOrange.copy(alpha = 0.15f),
                                        selectedLabelColor = brand,
                                        selectedLeadingIconColor = brand
                                    )
                                )
                            }
                        }
                    }
                }

                // 卡片 2：购买日期 + 名称 + 金额
                FormCard {
                    Column {
                        // 购买日期
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "购买日期",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(onClick = { showDatePicker = true }) {
                                Text(
                                    dateFormat.format(Date(viewModel.purchaseDate)),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        // 输入物品名称（左标签 + 右输入）
                        LabeledInputRow(
                            label = "物品名称",
                            value = viewModel.name,
                            onValueChange = viewModel::onNameChange,
                            placeholder = "输入物品名称",
                            keyboardType = KeyboardType.Text
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        // 物品金额（左标签 + 右输入）
                        LabeledInputRow(
                            label = "物品金额",
                            value = viewModel.purchasePriceText,
                            onValueChange = viewModel::onPurchasePriceChange,
                            placeholder = "物品金额",
                            keyboardType = KeyboardType.Decimal
                        )
                    }
                }

                // 卡片 3：附加费用 + 收益金额
                FormCard {
                    Column {
                        // 附加费用标题行
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "附加费用",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(
                                onClick = { },
                                colors = IconButtonDefaults.iconButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Filled.AddCircle, null)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        MoneySubRow(
                            label = "买配件",
                            value = viewModel.accessoryCostText,
                            onValueChange = viewModel::onAccessoryCostChange
                        )
                        Spacer(Modifier.height(8.dp))
                        MoneySubRow(
                            label = "维修费用",
                            value = viewModel.repairCostText,
                            onValueChange = viewModel::onRepairCostChange
                        )
                        Spacer(Modifier.height(16.dp))
                        // 收益金额
                        Text(
                            "收益金额",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        MoneySubRow(
                            label = "二手变现",
                            value = viewModel.resaleValueText,
                            onValueChange = viewModel::onResaleValueChange
                        )
                    }
                }

                // 校验错误提示
                viewModel.errorMessage?.let { msg ->
                    Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // ====== Step 14：图片上传全屏 overlay（Screen 2.5）======
            if (showImageUpload) {
                ImageUploadScreen(
                    viewModel = viewModel,
                    categoryKey = selectedCategory?.colorKey,
                    onPickCamera = {
                        // 创建相机临时文件 → 通过 FileProvider 获取 URI → 启动相机
                        val file = File(context.cacheDir, "temp_camera_${System.currentTimeMillis()}.jpg")
                        tempCameraFile = file
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        cameraLauncher.launch(uri)
                    },
                    onPickGallery = {
                        galleryLauncher.launch("image/*")
                    },
                    onDismiss = { showImageUpload = false }
                )
            }

            // ====== Step 14：放弃编辑确认弹窗 ======
            if (showDiscardDialog) {
                AlertDialog(
                    onDismissRequest = { showDiscardDialog = false },
                    title = { Text("放弃编辑？") },
                    text = { Text("已修改的内容将不会保存") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDiscardDialog = false
                                onDone()   // 用户确认放弃 → 关闭表单回首页
                            }
                        ) {
                            Text("放弃", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDiscardDialog = false }) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }

    // 自定义分类弹窗（复用 Screen 2.3 组件）
    if (showCustomDialog) {
        CustomCategoryDialog(
            existingNames = categories.map { it.name },
            onConfirm = { trimmedName ->
                scope.launch {
                    val db = WujiDatabaseProvider.get(context)
                    val newCat = CategoryEntity(
                        name = trimmedName,
                        colorKey = "custom",
                        isPreset = false,
                        sortOrder = (categories.maxOfOrNull { it.sortOrder } ?: -1) + 1
                    )
                    val newId = db.categoryDao().insert(newCat)
                    showCustomDialog = false
                    viewModel.onCategorySelect(newId)
                }
            },
            onDismiss = { showCustomDialog = false }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.purchaseDate
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            viewModel.onPurchaseDateChange(it)
                        }
                        showDatePicker = false
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ============================================================================
// 以下为 ItemEditScreen 的私有子组件（独立 @Composable 函数）
// ============================================================================

/**
 * 顶部图片占位区 / 已选图片展示区。
 * 有图片时显示圆角图片缩略图；无图时显示分类图标 + 相机角标。
 * 点击触发 onClick 回调（Step 14 接入 ImageUploadScreen）。
 */
@Composable
private fun ImagePlaceholder(
    categoryKey: String?,
    imagePath: String?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = decodeImagePath(imagePath)
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(WujiRadius.cardLg))
                .then(
                    if (bitmap != null) Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    else Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow)
                )
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    RoundedCornerShape(WujiRadius.cardLg)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                // 已选图片：显示圆角裁剪后的图片
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "物品图片",
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(WujiRadius.cardLg)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            } else {
                // 未选图片：显示分类图标 + 右下角相机角标
                Icon(
                    categoryIcon(categoryKey),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                )
            }
            // 相机角标（有图/无图都显示，提示可点击更换/添加）
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .offset((-2).dp, (-2).dp)
                    .background(BrandOrange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AddAPhoto,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * Screen 2.5：上传物品图片全屏页面 [参考设计截图 2.5]。
 *
 * 结构：
 * - CenterAlignedTopAppBar：← 返回 + "上传物品图片"
 * - 预览大区：已选图显示图片，未选显示分类图标 + 提示文字
 * - 底部两卡片横排：「拍照」（橙色圈+相机图标）+「相册」（灰色圈+相册图标）
 *
 * @param viewModel 当前物品编辑 ViewModel（读取/写入图片路径与展示态）
 * @param categoryKey 当前分类颜色键（用于未选图时的图标展示）
 * @param onPickCamera 点击「拍照」回调
 * @param onPickGallery 点击「相册」回调
 * @param onDismiss 点击返回 / 遮罩关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageUploadScreen(
    viewModel: ItemEditViewModel,
    categoryKey: String?,
    onPickCamera: () -> Unit,
    onPickGallery: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tintOrange = if (isDark) TintOrangeDark else TintOrangeLight
    val brand = if (isDark) BrandOrangeDark else BrandOrange

    // Step 16：抠图相关本地状态
    var processing by remember { mutableStateOf(false) }
    // 已存在抠图则直接进入对比态；首次抠图成功后展开
    var compareMode by remember { mutableStateOf(viewModel.croppedImagePath != null) }
    var viewCutout by remember { mutableStateOf(viewModel.imageDisplayMode == 1) }

    val hasImage = viewModel.imagePath != null
    val hasCutout = viewModel.croppedImagePath != null

    // 预览路径：对比态且选择「抠图」时显示透明 PNG，否则显示原图
    val previewPath = if (compareMode && viewCutout && hasCutout) {
        viewModel.croppedImagePath
    } else {
        viewModel.imagePath
    }
    val previewBitmap = decodeImagePath(previewPath)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)   // 不透明底，防止透出底层表单
    ) {
        // 顶栏（对齐截图：标题 + X关闭 + 右上角保存）
        CenterAlignedTopAppBar(
            title = { Text("编辑家具物品", style = MaterialTheme.typography.headlineSmall) },
            navigationIcon = {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            },
            actions = {
                // 右上角「保存」：保存当前图片/抠图选择并退出回表单
                TextButton(onClick = onDismiss) {
                    Text("保存", color = brand, fontWeight = FontWeight.Bold)
                }
            }
        )

        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(16.dp))

                // 预览大区（对齐截图：大圆角矩形容器）
                // 抠图预览态用棋盘格底色凸显透明区域；原图态保持 surfaceContainerLow
                val previewBg = if (compareMode && viewCutout && hasCutout)
                    Color(0xFFE0E0E0) // 中灰底（透明区可见，白边/阴影可辨）
                else
                    MaterialTheme.colorScheme.surfaceContainerLow
                Surface(
                    shape = RoundedCornerShape(WujiRadius.cardLg),
                    color = previewBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (previewBitmap != null) {
                            // 已选图片：完整显示（透明 PNG 透出容器底，呈贴纸效果）
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = "图片预览",
                                modifier = Modifier.matchParentSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        } else {
                            // 未选：分类图标灰显 + 提示文字
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    categoryIcon(categoryKey),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "请先上传一张清晰的照片",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // 两张选项卡片横排（对齐截图：拍照左 / 相册右）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    UploadOptionCard(
                        icon = Icons.Filled.CameraAlt,
                        label = "拍照",
                        iconTint = brand,
                        circleBg = tintOrange.copy(alpha = 0.15f),
                        onClick = onPickCamera,
                        modifier = Modifier.weight(1f)
                    )
                    UploadOptionCard(
                        icon = Icons.Filled.PhotoLibrary,
                        label = "相册",
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        circleBg = MaterialTheme.colorScheme.surfaceContainerHighest,
                        onClick = onPickGallery,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Step 16：智能抠图入口（有图且尚未进入对比态时显示）
                if (hasImage && !processing && !compareMode) {
                    Spacer(Modifier.height(16.dp))
                    CutoutEntryCard(
                        onClick = {
                            viewModel.clearCutoutError()
                            processing = true
                            viewModel.runCutout(context) { ok ->
                                processing = false
                                if (ok) {
                                    compareMode = true
                                    viewCutout = true
                                }
                                // 失败时 viewModel.cutoutError 已被设置（含异常详情）
                            }
                        }
                    )
                }

                // Step 16：抠图对比控制（已生成抠图时显示）
                if (compareMode && hasCutout && !processing) {
                    Spacer(Modifier.height(16.dp))
                    // 原图 / 抠图 胶囊切换
                    Row(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(999.dp)
                            )
                            .padding(4.dp)
                    ) {
                        SegmentedToggle(
                            selected = !viewCutout,
                            text = "原图",
                            onClick = { viewCutout = false },
                            modifier = Modifier.weight(1f)
                        )
                        SegmentedToggle(
                            selected = viewCutout,
                            text = "抠图",
                            onClick = { viewCutout = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    // 保留原图 / 使用抠图
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        androidx.compose.material3.Button(
                            onClick = {
                                viewModel.onKeepOriginal()
                                compareMode = false
                                viewCutout = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) { Text("保留原图", fontWeight = FontWeight.Bold) }
                        androidx.compose.material3.Button(
                            onClick = {
                                viewModel.onUseCutout()
                                compareMode = false
                                viewCutout = true
                            },
                            modifier = Modifier.weight(2f),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = brand
                            )
                        ) { Text("使用抠图", color = Color.White, fontWeight = FontWeight.Bold) }
                    }
                }

                // 抠图失败提示（含具体异常信息，便于排查）
                viewModel.cutoutError?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(24.dp))
                // 确认按钮：确认当前图片/抠图选择（不退出，留在当前页）
                // 有抠图结果时自动设为「使用抠图」模式；点击右上角「保存」才退出回表单
                androidx.compose.material3.Button(
                    onClick = {
                        // 有抠图结果时，确认即代表选用抠图
                        if (hasCutout) {
                            viewModel.onUseCutout()
                            viewCutout = true
                        }
                        // 不调用 onDismiss，留在当前页供用户继续调整或点保存退出
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = brand
                    )
                ) {
                    Text("确认", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
            }

            // Step 16：处理中遮罩（正在智能抠图...）
            if (processing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.material3.CircularProgressIndicator(color = brand)
                        Spacer(Modifier.height(12.dp))
                        Text("正在智能抠图...", color = brand, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Step 16：智能抠图入口卡片（auto_fix_high 图标 + 文案）。 */
@Composable
private fun CutoutEntryCard(onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val brand = if (isDark) BrandOrangeDark else BrandOrange
    Surface(
        shape = RoundedCornerShape(WujiRadius.cardLg),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.dp, brand.copy(alpha = 0.15f), RoundedCornerShape(WujiRadius.cardLg))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(brand, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.AutoFixHigh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "智能抠图",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = brand
                )
                Text(
                    "AI 自动去除背景，生成高清商品图",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = brand.copy(alpha = 0.6f)
            )
        }
    }
}

/** Step 16：原图 / 抠图 分段切换按钮。 */
@Composable
private fun SegmentedToggle(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val brand = if (isDark) BrandOrangeDark else BrandOrange
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceContainerLowest
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontWeight = FontWeight.Bold,
            color = if (selected) brand else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 上传方式选项卡片：圆形图标底 + 文字标签。用于拍照/相册两个入口。 */
@Composable
private fun UploadOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    iconTint: Color,
    circleBg: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(WujiRadius.cardLg),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier
            .clickable(onClick = onClick)
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(WujiRadius.cardLg),
                ambientColor = Color.Black.copy(alpha = 0.02f),
                spotColor = Color.Black.copy(alpha = 0.02f)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(WujiRadius.cardLg)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 图标圆圈
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(circleBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            // 标签文字
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** 表单卡片容器：白底圆角 + 软阴影 + 细描边，内边距 16dp。三张表单卡片共用。 */
@Composable
private fun FormCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(WujiRadius.cardLg),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(WujiRadius.cardLg),
                ambientColor = Color.Black.copy(alpha = 0.02f),
                spotColor = Color.Black.copy(alpha = 0.02f)
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(WujiRadius.cardLg)
            )
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** 左标签 + 右输入行：用于物品名称/金额，左侧字段名 + 右侧输入框 + × 清除按钮。 */
@Composable
private fun LabeledInputRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(16.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(
                    placeholder,
                    textAlign = TextAlign.End,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = TextStyle(
                textAlign = TextAlign.End,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            trailingIcon = if (value.isNotEmpty()) {
                {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            } else null,
            colors = transparentTextFieldColors(),
            modifier = Modifier.weight(1f)
        )
    }
}

/** 附加费用 / 收益金额的子金额行：浅底圆角容器，左侧标签 + 右侧右对齐数字输入。 */
@Composable
private fun MoneySubRow(label: String, value: String, onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLow,
                RoundedCornerShape(WujiRadius.md)
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            textStyle = TextStyle(
                textAlign = TextAlign.End,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            ),
            placeholder = {
                Text(
                    "0.00",
                    textAlign = TextAlign.End,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            colors = transparentTextFieldColors(),
            modifier = Modifier.width(120.dp)
        )
    }
}

/** 状态点语义色：使用中绿、未使用灰、已售出橙（固定语义，不随主题翻转）。 */
private fun statusDotColor(status: ItemStatus): Color = when (status) {
    ItemStatus.IN_USE -> Color(0xFF22C55E)
    ItemStatus.UNUSED -> Color(0xFF9E9E9E)
    ItemStatus.SOLD -> BrandOrange
}

/** 无边框输入样式：透明容器 + 透明指示线，仅保留光标。 */
@Composable
private fun transparentTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.colorScheme.primary,
)
