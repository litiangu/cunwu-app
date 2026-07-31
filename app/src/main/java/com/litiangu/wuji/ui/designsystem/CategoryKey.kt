package com.litiangu.wuji.ui.designsystem

data class CategoryKey(val name: String) {
    companion object {
        fun fromName(name: String): CategoryKey = CategoryKey(name)
    }
}

fun categoryLabel(key: CategoryKey): String = key.name
