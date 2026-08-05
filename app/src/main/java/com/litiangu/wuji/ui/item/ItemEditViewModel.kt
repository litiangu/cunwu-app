package com.litiangu.wuji.ui.item

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.litiangu.wuji.data.db.CategoryDao
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
 */
class ItemEditViewModel(
    private val itemDao: ItemDao,
    private val categoryDao: CategoryDao,
    private val itemId: Long?,
    initialCategoryId: Long? = null,   // Step 13 方案 A：预选分类由构造参数注入，摒弃 LaunchedEffect 副作用
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
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val categories: StateFlow<List<CategoryEntity>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 编辑时保留原始 createdAt；新增时用当前时间
    private var originalCreatedAt = System.currentTimeMillis()

    init {
        if (itemId != null) {
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
                    originalCreatedAt = item.createdAt
                }
            }
        }
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
                imagePath = null,
                croppedImagePath = null,
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
