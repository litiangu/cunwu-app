package com.litiangu.wuji.ui.home

/**
 * 首页三视图模式（Step 40）。
 *
 * 之前全 App 到处散着裸字符串 "list-default" / "card-2x2" / "list-compact"，
 * 写错一个字母编译期查不出来。改成枚举后，UI 侧拿到的就是类型安全的值。
 *
 * DataStore 里**仍然存老字符串**（兼容老用户已有数据），字符串与枚举的互转
 * 收口在 `SettingsDataStore`，字符串不往存储层之外泄漏。
 */
enum class HomeViewMode(val storageKey: String) {
    /** 模式 A：默认列表（按购入月份分组，80dp 行高） */
    DEFAULT("list-default"),

    /** 模式 B：卡片瀑布流（Step 42 落地，当前仍为等高 2×2 网格） */
    CARD("card-2x2"),

    /** 模式 C：紧凑陈列馆（Step 43 落地，当前仍为 36dp 圆图小格） */
    COMPACT("list-compact"),
    ;

    companion object {
        /** 存储字符串 → 枚举；空值或历史脏数据一律回退 DEFAULT，不让老用户卡在非法状态。 */
        fun fromStorageKey(key: String?): HomeViewMode =
            entries.firstOrNull { it.storageKey == key } ?: DEFAULT
    }
}
