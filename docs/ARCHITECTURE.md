# 架构与技术

> 目录结构、技术栈选型、路线图。返回 [README](../README.md)

---

## 目录结构

```
com.litiangu.wuji
├── MainActivity.kt              单 Activity + 布尔状态机 overlay 导航栈
├── WujiApplication.kt           预热仓库 + isReady 闸门，消除冷启动白屏
├── domain/
│   └── HealthScore.kt           健康分引擎（纯 Kotlin，无 Android 依赖）
├── data/
│   ├── db/                      Room：Item / Category / Trash / CategoryBudget
│   └── settings/                DataStore + 备份、预算、资料仓库
└── ui/
    ├── onboarding/  home/  item/  analysis/  settings/  category/
    ├── designsystem/            Haptics、Token、图标
    └── theme/                   Type / Color Token
```

**导航约定**：项目**没有**引入 Navigation Compose。`MainActivity.WujiApp` 用一组 `var showXxx by remember` 布尔量表达导航状态，Box 叠放顺序即导航栈顺序（后组合的在上层）。每个全屏 overlay 必须自带 `BackHandler`，否则侧滑会穿透到下层。新增页面时按栈位插入，勿追加在末尾。

**设计令牌**：`ui/theme/Tokens.kt` 是唯一色值与尺寸事实源。卡片底色用 `surfaceContainerLowest`（浅色 = `#FFFFFF`）而非 `surface`（浅色 = `#FAF9F9`，与页面背景同色会导致隐形）。

---

## 技术栈

| 类别 | 选型 | 版本 |
| :--- | :--- | :--- |
| 语言 | Kotlin | 2.2.10 |
| UI | Jetpack Compose + Material 3 | BOM 2026.02.01 |
| 架构 | MVVM + 单 Activity overlay 状态机 | — |
| 持久化 | Room（KSP）+ DataStore Preferences | 2.8.4 / 1.2.1 |
| 序列化 | kotlinx.serialization-json | 1.9.0 |
| 端侧推理 | Google LiteRT + ormbg（ISNet, Apache-2.0） | 2.1.0 / 176 MB |
| 构建 | Gradle + AGP + KSP | 9.5.0 / 9.3.1 / 2.2.10-2.0.2 |
| SDK | minSdk 34 · targetSdk 36 · compileSdk 36.1 | — |
| 测试 | JUnit 4（JVM）+ AndroidX Test | 4.13.2 / 1.1.5 |

---

## 路线图

- [x] 健康分二维模型（v5）
- [x] 端侧智能抠图 + 贴纸化
- [x] 首页三视图
- [x] 分析页组件重排与健康卡同心多环
- [x] 深色模式三档
- [x] 含图 zip 备份与还原
- [x] 回收站 30 天自动清理
- [x] 隐私模式金额脱敏
- [x] Apache-2.0 开源许可与 README 文档
- [x] release 签名配置（环境变量读取，密钥已生成）
- [x] release 体积优化：**254 MB → 179 MB**（dex −95%，仅 arm64-v8a）
- [x] `v1.0.0` Release 构建产出（179.2 MB，`apksigner verify` 通过，v3 签名方案）
- [x] **Release 真机回归通过**（抠图 / 备份还原 / 冷启动，R8 无副作用）
- [ ] GitHub Releases 发布与 tag
- [ ] 应用商店素材（图标 / 截图 / 描述）
- [ ] 隐私政策定稿

### 分发说明

APK **不进 git 仓库**，挂 GitHub Releases 附件。原因：APK 体积 179 MB，
GitHub 仓库单文件硬限制 100 MiB，放进去会被 push 直接拒绝。
完整发布流程见 [`RELEASE.md`](RELEASE.md)。

---
