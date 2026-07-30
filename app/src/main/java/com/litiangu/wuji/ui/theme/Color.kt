package com.litiangu.wuji.ui.theme

import androidx.compose.ui.graphics.Color

// 品牌主色（设计令牌 primary #FF5500）
val BrandOrange = Color(0xFFFF5500)

// 分类 7 色（固定语义色，不随深色主题翻转；来源：首页分类筛选栏功能）
val CatDigital   = Color(0xFFFF5500) // 数码橙
val CatAppliance = Color(0xFF0088FF) // 电器蓝
val CatFurniture = Color(0xFF2E7D32) // 家具绿
val CatTool      = Color(0xFFFFB300) // 工具黄
val CatSport     = Color(0xFF9C27B0) // 运动紫
val CatTraffic   = Color(0xFF00BCD4) // 交通青
val CatCustom    = Color(0xFF9E9E9E) // 自定义灰

// 健康分四档评级色（固定语义色；来源：健康分加权模型设计草案）
val GradeExcellent = Color(0xFF34C759) // ≥85 绿
val GradeGood      = Color(0xFF0088FF) // 70-84 蓝
val GradeFair      = Color(0xFFFFB300) // 50-69 黄
val GradePoor      = Color(0xFFBA1A1A) // <50 红
