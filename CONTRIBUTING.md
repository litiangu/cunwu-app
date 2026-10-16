# 贡献指南

> 感谢你愿意参与。第一条建议：**动手前先开 Issue 描述你想做什么**，避免大方向分歧后返工。

相关文档：[README](README.md) · [构建指南](docs/BUILD.md) · [发布流程](docs/RELEASE.md)

---

# 贡献指南



## 环境准备

```bash
git clone https://github.com/litiangu/cunwu-app.git
cd cunwu-app
git lfs pull          # 别忘了
# 按 README 第 3、4 步配置 JDK 与 SDK
```

## 分支策略

```bash
git checkout -b feat/your-feature      # 新功能
git checkout -b fix/your-bug           # 缺陷修复
git checkout -b docs/your-topic        # 文档
```

分支从 `main` 切出，命名用 `feat/` `fix/` `docs/` 前缀 + 简短描述。

## 提交信息

项目沿用**中文提交信息 + 里程碑 tag** 的历史风格（77 个 tag），建议照此格式：

```
feat(analysis): 分析页新增近 30 天分类支出柱状图

- 新增 CategorySpendBar 组件，读 DIMENSION_WEIGHTS 单点真相
- 空数据状态渲染空态插画，不显示 0 值柱
- 补充 AnalysisViewModel.prefetchCategorySpend()
```

类型前缀：`feat` / `fix` / `refactor` / `docs` / `test` / `build` / `perf` / `chore`

## Pull Request 要求

- [ ] `gradlew.bat assembleDebug` 在你的机器上通过
- [ ] 新增算法逻辑附带 JVM 单元测试（`app/src/test/`）
- [ ] 纯函数优先。`domain/HealthScore.kt` 保持零 Android 依赖，这是它可被 JVM 测试完整覆盖的原因
- [ ] 新增颜色 / 尺寸写进 `ui/theme/Tokens.kt`，**不要在 Composable 里硬编码色值**
- [ ] UI 改动附深浅两色模式截图
- [ ] PR 描述写清「改了什么 / 为什么 / 怎么验证」
- [ ] 一次 PR 只做一件事

## 代码风格

```bash
./gradlew ktlintFormat    # 若后续接入
```

当前约定：

- Kotlin 官方风格（`kotlin.code.style=official`），4 空格缩进
- Compose 里用 `by remember { mutableStateOf(...) }` **必须**同时导入 `getValue` 与 `setValue`
- 图标优先用 `material-icons-core`；用 `extended` 里的图标需确认依赖已声明
- 注释写「为什么」，不写「做了什么」

## 领域约定（血泪教训，请先读）

- **抠图问题先 dump 像素，别靠截图推测**。`cutout_debug` 探针默认开启，每次抠图都会落盘 `1_raw_norm.png` / `2_final_mask.png` / `3_final_sticker.png` / `4_cutout.png` / `info.txt`，用 `adb pull` 拉回分析。判断「颜色问题还是透明问题」可用对照实验法：**同一个像素在浅底显浅色、深底显黑色，通常说明透明**。但若原图该像素本就是纯黑，此推理失效，需先采原图像素确认。
- **改抠图引擎后必须重新抠图**。结果持久化在 PNG 里，改代码不会回溯更新旧图。
- **全屏 overlay 必须自带 `BackHandler`**，返回语义恒为「关本页」，不重置下层状态。
- **Compose `weight(f)` 是比例分配不是百分比**。表达「占 23%」必须给剩余段补 `weight(0.77)` 占位子项。
- **多栏行级对齐禁用 `SpaceBetween`**，行数不同时会错位。用固定 `spacedBy` 或显式 padding。

## 开发流程

项目的节奏是**单步闭环**，请照此提交：

```
一个 md 计划 → 写码 → 本机 assembleDebug → 设备验收 → commit + tag → push
```

一次只推进一步。已 push 的步骤若再修正，用 `amend` + 移动 tag，配合 `--force-with-lease` 强推。

## 🚫 请勿提交的内容

| 内容 | 原因 |
| :--- | :--- |
| `*.apk` / `*.aar` | GitHub 单文件硬限制 100 MiB，APK 约 254 MB，push 会被直接拒绝 |
| `*.jks` / `*.p12` / `*.keystore` | 签名密钥泄露等于应用更新权公开 |
| 口令、密钥路径 | 通过环境变量传入，见 `docs/RELEASE.md` |
| `local.properties` | 含本机 SDK 绝对路径 |
| `.gradle/` `build/` | 构建产物 |

想分发 APK 请走 GitHub Releases，那是仓库唯一支持大文件的位置。

## 🧪 R8 相关（涉及 release 构建时）

release 已开启 `optimization { enable = true }`（AGP 9.3 新 DSL，同时开启代码优化与资源裁剪）。
新增以下能力时需同步检查 `app/src/main/keepRules/rules.keep`，否则编译期无感、运行期才崩：

| 新增能力 | 需要 keep 的原因 |
| :--- | :--- |
| `@Serializable` 类 | 反序列化靠 `Companion.serializer()` 反射构造 |
| Room `@Entity` / `@Dao` | 运行期反射构造 |
| 端侧模型推理 | LiteRT JNI 桥接层反射 |
| JSON/日期等新解析库 | 各自需 consumer rules |

改完必须真机回归三条路径：**抠图、备份还原、冷启动数据库读取**。

---
