package com.litiangu.wuji.ui.item

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import android.content.Context
import androidx.lifecycle.viewModelScope
import com.litiangu.wuji.data.db.CategoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.litiangu.wuji.data.db.CategoryEntity
import com.litiangu.wuji.data.db.ItemDao
import com.litiangu.wuji.data.db.ItemEntity
import com.litiangu.wuji.data.db.ItemStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 新增 / 编辑物品的表单状态与持久化。
 * itemId == null 表示新增，否则编辑该 id 的物品（init 里回显）。
 * initialCategoryId：分类选择页预选的分类 id（Step 12/13，方案 A：构造参数直接注入）。
 *
 * Step 14 新增：
 * - imagePath：物品图片本地路径（选图后由 ImageUploadScreen 回填）
 * - baseline / isDirty：脏检测，用于放弃保护（× 按钮 + 系统返回拦截）
 */
class ItemEditViewModel(
    private val itemDao: ItemDao,
    private val categoryDao: CategoryDao,
    private val itemId: Long?,
    initialCategoryId: Long? = null,   // Step 13 方案 A：预选分类由构造参数直接注入
) : ViewModel() {

    var name by mutableStateOf("")
        private set
    // 新增模式用构造参数预选；编辑模式由下方 init 回显覆盖（调用侧不传此参）
    var categoryId by mutableStateOf<Long?>(initialCategoryId)
        private set
    var status by mutableStateOf(ItemStatus.IN_USE)
        private set
    var purchaseDate by mutableStateOf(System.currentTimeMillis())
        private set
    var purchasePriceText by mutableStateOf("")
        private set
    // Step 13：附加费用拆为两个子字段（买配件 / 维修费用），保存时求和写入 extraCost 单列
    var accessoryCostText by mutableStateOf("")
        private set
    var repairCostText by mutableStateOf("")
        private set
    var resaleValueText by mutableStateOf("")
        private set
    // Step 14：图片路径（null = 未上传）
    var imagePath by mutableStateOf<String?>(null)
        private set
    // Step 16：抠图结果（透明 PNG 路径）；imagePath 原图始终保留
    var croppedImagePath by mutableStateOf<String?>(null)
        private set
    // Step 16：展示态 0=原图 1=抠图
    var imageDisplayMode by mutableStateOf(0)
        private set
    var cutoutError by mutableStateOf<String?>(null)  // Step16：抠图失败详情（含异常信息）

    /** 清除抠图错误状态（重试前调用） */
    fun clearCutoutError() { cutoutError = null }
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 编辑时保留原始 createdAt；新增时用当前时间
    private var originalCreatedAt = System.currentTimeMillis()

    // ====== Step 14：脏检测 baseline ======
    // 捕获进入表单时的全部可编辑字段快照。isDirty 通过与当前状态比较判断是否有改动。
    private data class Baseline(
        val name: String,
        val categoryId: Long?,
        val status: ItemStatus,
        val purchaseDate: Long,
        val purchasePriceText: String,
        val accessoryCostText: String,
        val repairCostText: String,
        val resaleValueText: String,
        val imagePath: String?,
        val croppedImagePath: String?,
        val imageDisplayMode: Int
    )
    private var baseline: Baseline? = null

    /** 当前是否处于「脏」状态（相对进入时的基线有改动）。用于放弃保护弹窗触发判断。 */
    val isDirty: Boolean
        get() {
            val b = baseline ?: return false
            return b != Baseline(
                name, categoryId, status, purchaseDate,
                purchasePriceText, accessoryCostText, repairCostText,
                resaleValueText, imagePath,
                croppedImagePath, imageDisplayMode
            )
        }

    init {
        if (itemId != null) {
            // 编辑模式：从数据库加载物品数据并回显 + 捕获 baseline
            viewModelScope.launch {
                itemDao.getById(itemId)?.let { item ->
                    name = item.name
                    categoryId = item.categoryId
                    status = item.status
                    purchaseDate = item.purchaseDate
                    purchasePriceText = formatAmount(item.purchasePrice)
                    // 旧数据无拆分，整体归入「买配件」，维修费用置 0
                    accessoryCostText = formatAmount(item.extraCost)
                    repairCostText = "0.00"
                    resaleValueText = formatAmount(item.resaleValue)
                    imagePath = item.imagePath   // Step 14：加载已有图片路径
                    croppedImagePath = item.croppedImagePath // Step 16：加载已有抠图
                    imageDisplayMode = item.imageDisplayMode // Step 16：加载展示态
                    originalCreatedAt = item.createdAt
                    // 编辑模式 baseline 在加载完成后捕获（此时所有字段已赋值）
                    captureBaseline()
                }
            }
        } else {
            // 新增模式：baseline 立即捕获（初始值即为基线，任意输入即脏）
            captureBaseline()
        }
    }

    /** 捕存当前全部字段为 baseline 快照 */
    private fun captureBaseline() {
        baseline = Baseline(
            name, categoryId, status, purchaseDate,
            purchasePriceText, accessoryCostText, repairCostText,
            resaleValueText, imagePath,
            croppedImagePath, imageDisplayMode
        )
    }

    fun onNameChange(value: String) {
        name = value.take(50)   // 名称上限 50 字符（逻辑文档 2.3）
        errorMessage = null
    }

    fun onCategorySelect(id: Long?) {
        categoryId = id
    }

    fun onStatusSelect(s: ItemStatus) {
        status = s
    }

    fun onPurchaseDateChange(millis: Long) {
        purchaseDate = millis
    }

    fun onPurchasePriceChange(value: String) {
        purchasePriceText = value
        errorMessage = null
    }

    fun onAccessoryCostChange(value: String) {
        accessoryCostText = value
    }

    fun onRepairCostChange(value: String) {
        repairCostText = value
    }

    fun onResaleValueChange(value: String) {
        resaleValueText = value
    }

    /** Step 14：图片选择完成后的回调，存储图片本地路径。重选原图时清空已有抠图结果。 */
    fun onImageChange(path: String?) {
        imagePath = path
        croppedImagePath = null
        imageDisplayMode = 0
    }

    // ====== Step 16：智能抠图相关 ======

    /** 抠图成功：写入透明 PNG 路径，原图 imagePath 保持不变 */
    fun onCutoutDone(croppedPath: String) {
        croppedImagePath = croppedPath
    }

    /** 用户选择「使用抠图」：展示态置为抠图 */
    fun onUseCutout() {
        imageDisplayMode = 1
    }

    /** 用户选择「保留原图」：展示态回退原图（抠图结果仍保留，便于再次切换） */
    fun onKeepOriginal() {
        imageDisplayMode = 0
    }

    /**
     * 触发端侧抠图：解码原图 → ormbg-LiteRT（ISNet）高质量去背 + 贴纸化后处理（白边+阴影）→ 存透明 PNG。
     * 在 viewModelScope + Dispatchers.Default 执行重算法，结果回主线程写状态。
     * @param onDone 回调：true=成功生成抠图，false=失败（模型缺失 / 无清晰主体）
     */
    fun runCutout(context: Context, onDone: (Boolean) -> Unit) {
        val originalPath = imagePath ?: run { onDone(false); return }
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val original = decodeImagePath(originalPath)
                    ?: throw IllegalStateException("无法解码原图")
                val sticker = SmartCutoutEngine.segmentSubject(context, original)
                val saved = saveCutoutBitmap(context, sticker)
                    ?: throw IllegalStateException("保存抠图失败")
                withContext(Dispatchers.Main) {
                    onCutoutDone(saved)
                    onDone(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val msg = "智能抠图失败: ${e.message}"
                withContext(Dispatchers.Main) {
                    cutoutError = msg
                    onDone(false)
                }
            }
        }
    }

    /** 校验后保存；成功回调 onSaved 返回上一页 */
    fun save(onSaved: () -> Unit) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            errorMessage = "名称不能为空"
            return
        }
        if (trimmed.length > 50) {
            errorMessage = "名称不超过 50 字"
            return
        }
        val price = purchasePriceText.trim().toDoubleOrNull()
        if (price == null) {
            errorMessage = "购入价需为数字"
            return
        }
        if (price < 0) {
            errorMessage = "购入价不能为负"
            return
        }
        if (purchaseDate > System.currentTimeMillis()) {
            errorMessage = "购买日期不能晚于今天"
            return
        }
        // 附加费用两子字段求和（空串按 0）
        val extra = (accessoryCostText.trim().toDoubleOrNull() ?: 0.0) +
                    (repairCostText.trim().toDoubleOrNull() ?: 0.0)
        val resale = resaleValueText.trim().toDoubleOrNull() ?: 0.0

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val entity = ItemEntity(
                id = itemId ?: 0,
                name = trimmed,
                categoryId = categoryId,
                status = status,
                purchaseDate = purchaseDate,
                purchasePrice = price,
                extraCost = extra,
                resaleValue = resale,
                imagePath = imagePath,          // Step 14：写入图片路径
                croppedImagePath = croppedImagePath, // Step 16：写入抠图结果
                imageDisplayMode = imageDisplayMode, // Step 16：写入展示态
                healthScore = null,
                createdAt = originalCreatedAt,
                updatedAt = now
            )
            if (itemId == null) {
                itemDao.insert(entity)
            } else {
                itemDao.update(entity)
            }
            onSaved()
        }
    }

    private fun formatAmount(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
