package com.litiangu.wuji.ui

/**
 * [Step 55] 全屏 overlay 路由定义。
 *
 * ── 为什么要从「一堆布尔」换成 sealed ─────────────────────────
 *
 * 改造前`WujiApp` 用 10 个互相独立的 Boolean / Long? 表示导航状态
 * （showAdd / showSettings / showAnalysis / showEdit / showCategorySelect …），
 * 在 Box 里用 `if` 直接挂载。这套写法有两个绕不开的问题：
 *
 * 1. **返回栈无处安放**（问题 2：侧滑返回退到桌面）。
 *    Activity 内只要有一个页面没注册 `BackHandler`，系统返回手势就默认
 *    结束整个 Activity。原先只有分析页、分类选择页、表单页三处注册，
 *    设置页及其六个子页全漏，症状就是「侧滑直接退桌面，只有顶部箭头能返回」。
 *    逐页补 `BackHandler` 治标不治本，新增页面还会重犯。
 *
 * 2. **进出场动画无处安放**（问题 3：界面切换生硬）。
 *    布尔从 false 变 true 的瞬间 composable 凭空出现，Compose 没有插值的
 *    容身之处，只能硬切。给 `if` 包 `AnimatedVisibility` 也不对，
 *    因为多个布尔可以同时为真，动画会互相打架。
 *
 * 换成单个 sealed 值后，一次拿到三样东西：
 *   · **栈序**：列表顺序即导航栈顺序，返回逻辑照着弹尾即可
 *   · **参数**：itemId / preselectedCategoryId 直接做构造属性，不用并行布尔
 *   · **转场**：可直接喂给 `AnimatedContent(targetState = ...)`
 *
 * ── 与既有约定的关系 ─────────────────────────────────────────
 *
 * 项目原有一条约定：「Box 内 overlay 叠放顺序 = 导航栈顺序（后组合的更上层）」。
 * 改成单 overlay 值后这条约定依然成立，只是从「组合次序」变成「栈的顺序」，
 * `AnimatedContent` 按 targetState 只渲染栈顶内容，天然不会错位。
 *
 * ── 栈的存放 ─────────────────────────────────────────────────
 *
 * 用 `mutableStateListOf<Overlay>` 而非单个 `Overlay?`：
 * 后者只能表达「最多一层」，会让「设置页 → 最近删除 → 返回后设置页原样露出」
 * 这类需求丢失。列表才能表达真正的栈。
 */

/** 全屏 overlay 路由。各分支的参数随路由一起携带，避免并行布尔。 */
sealed interface Overlay {

    /** 分析页。顶栏无返回箭头（4.0 设计稿形态），返回路径只有系统返回。 */
    data object Analysis : Overlay

    /** 分类选择页。FAB 与引导页 CTA 的入口。 */
    data object CategorySelect : Overlay

    /** 新增物品表单。[preselectedCategoryId] 为从分类选择页带入的预选分类。 */
    data class ItemAdd(val preselectedCategoryId: Long? = null) : Overlay

    /** 编辑物品表单。[itemId] 是被编辑物品的主键。 */
    data class ItemEdit(val itemId: Long) : Overlay

    /** 设置页。设置页的六个子页（预算锚点 / 最近删除 / 备份 / 关于 / 用户信息 / 法律）叠在它之上。 */
    data object Settings : Overlay

    /** 预算锚点编辑子页。 */
    data object BudgetAnchor : Overlay

    /** 最近删除（回收站）列表页。 */
    data object Trash : Overlay

    /** 数据备份（导出 / 导入）页。 */
    data object Backup : Overlay

    /** 关于页。 */
    data object About : Overlay

    /** 用户信息编辑（昵称 + 头像）页。 */
    data object ProfileEdit : Overlay

    /**
     * 关于页内的法律条款子页（用户协议 / 开源许可）。
     *
     * 改造前这是 `AboutScreen.kt` 内部的 `legalPage` 私有状态，
     * 根层拿不到，也就无法纳入统一返回栈 —— 侧滑返回时这一层会漏。
     * [Step 55] 把它提升到根层，与其他 overlay 平级。
     */
    data class AboutLegal(val page: LegalPage) : Overlay
}

/**
 * 法律内容枚举。原先是 `AboutScreen.kt` 的 private enum，
 * 提升为 public 才能被 [Overlay.AboutLegal] 引用。
 */
enum class LegalPage {
    USER_AGREEMENT,
    OPEN_SOURCE,
}

/**
 * 是否为「弹窗类」overlay：转场用缩放 + 淡入，而非页面类的横向滑动。
 *
 * 目前只有法律条款页走缩放。它虽然是全屏页，但视觉上属于「浮在关于页之上的一层」，
 * 用滑动感更贴近「打开一份文档」的心智模型。
 */
val Overlay.isDialog: Boolean get() = this is Overlay.AboutLegal

/**
 * 栈内是否已存在指定 overlay。用于避免重复 push 同一页
 * （例如连点两次齿轮，把设置页压成两层，返回时要按两次才回首页）。
 */
fun List<Overlay>.containsOverlay(target: Overlay): Boolean = any { it == target }