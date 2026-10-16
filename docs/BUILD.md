# 构建指南

> 从源码构建存物 Cunwu。返回 [README](../README.md)

---

## 环境要求

| 项目 | 版本 | 说明 |
| :--- | :--- | :--- |
| **JDK** | **21** | AGP 9.3 需要含 `jmods` 的完整 JDK 21，JBR 不满足 |
| **Android Studio** | Meerkat 或更新 | 需支持 AGP 9.x |
| **Gradle** | 9.5.0 | 由 `gradle-wrapper.properties` 自动下载，无需手装 |
| **compileSdk** | 36.1 | `version = release(36) { minorApiLevel = 1 }` |
| **minSdk** | 34 | Android 14 |
| **targetSdk** | 36 | Android 16 |
| **Git LFS** | 3.x | **必需**，用于拉取 176 MB 抠图模型 |

---

## 安装指南

## 第 1 步 · 克隆仓库

```bash
git clone https://github.com/litiangu/cunwu-app.git
cd cunwu-app
```

> 仓库曾用名 `wuji-app-demo`，现已更名为 `cunwu-app`。
> GitHub 对重命名仓库保留旧地址的 301 重定向，旧克隆的本地 remote 仍能正常推送，
> 但建议顺手改到新地址（顺便换 SSH，比 HTTPS 可靠）：
> ```bash
> git remote set-url origin git@github.com:litiangu/cunwu-app.git
> git remote -v   # 确认已指向 cunwu-app
> ```

## 第 2 步 · 拉取 Git LFS 模型 ⭐

这一步最容易被漏掉。**跳过它，App 会在抠图时崩溃**。

```bash
# 确认已安装 Git LFS
git lfs version

# 拉取全部 LFS 内容（约 176 MB）
git lfs pull
```

验证是否成功：

```bash
# 输出约 168 MB 说明 LFS 内容已就位
ls -lh app/src/main/assets/ormbg.tflite
```

如果文件只有约 130 字节，说明拿到的是 LFS 指针文本，`LiteRT` 加载时会抛异常。`SmartCutoutEngine` 已内置指针文件前置校验并给出明确报错，但提前 `git lfs pull` 更省事。

> ⚠️ 模型走 `androidResources { noCompress.add("tflite") }`，禁止被 aapt 压缩，否则 `mmap` 加载失败。请勿改动这条配置。

## 第 3 步 · JDK要求

**本仓库不硬编码任何 JDK 路径**，clone 后开箱即可构建。

工程在 `settings.gradle.kts` 里启用了 `foojay-resolver-convention`，
Gradle 会自行解析合适的 JDK 工具链，无需配置 `org.gradle.java.home` 或 `JAVA_HOME`。

### 硬性要求

AGP 9.3 需要 **JDK 21**，且必须是**含 `jmods` 的完整 JDK**
（Android Studio 内置的 JBR 不满足，jlink 阶段会崩）。

foojay 默认解析 Temurin JDK（含 `jmods`），符合要求，无需额外操作。

### 如果构建失败

报 `JdkImageTransform` / jlink 崩溃，说明 Gradle 拿到了不含 `jmods` 的 JDK
（例如某些环境把 `JAVA_HOME` 指向了 JBR）。此时用环境变量显式指定：

```powershell
# Windows：永久设置
setx JAVA_HOME "C:\path\to\jdk-21"

# macOS / Linux：当前会话
export JAVA_HOME=/path/to/jdk-21
```

Linux 环境下还可以装系统的 OpenJDK 21：

```bash
sudo apt install openjdk-21-jdk
```

### 关于 `local.properties`

`local.properties` 同样不入版本库，需按第 4 步本地生成。

## 第 4 步 · 配置 SDK 路径

`local.properties` 不入版本库，需本地生成：

```properties
# Windows
sdk.dir=C\:\\Users\\<你的用户名>\\AppData\\Local\\Android\\Sdk

# macOS / Linux
sdk.dir=/Users/<用户名>/Library/Android/sdk
```

用 Android Studio 打开项目会自动生成此文件，可跳过手工创建。

## 第 5 步 · 构建

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`（约 **254 MB**，其中 ormbg 模型占 168 MB）

> ⚠️ **体积提示**：debug APK 约 **254 MB**，release 约 **179 MB**。
> 其中 ormbg 模型独占 168 MB，这是 `noCompress` 的必然代价
> （模型禁止压缩，否则 LiteRT 无法 `mmap` 加载），压不动。
> 首次构建需要下载依赖与 LFS 模型，请预留时间和磁盘。

> 📌 debug 包名为 `com.litiangu.wuji.debug`，可与正式版并存安装。

## 第 6 步 · 安装到设备

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或用 Android Studio 的 Run 按钮（Shift + F10）。

## 第 7 步 ·（可选）构建 release 版

想要带签名、体积更小、关闭日志的正式包，走这条路：

```bash
gradlew.bat signingRelease      # 先确认签名配置生效
gradlew.bat assembleRelease
```

签名密钥通过四个环境变量传入（`CUNWU_KEYSTORE_PATH` / `CUNWU_KEYSTORE_PASSWORD` /
`CUNWU_KEY_ALIAS` / `CUNWU_KEY_PASSWORD`）。缺失时 release 会走未签名分支，
产物装不上，这是刻意设计。完整步骤见 [`RELEASE.md`](RELEASE.md)。

## 🩺 常见构建问题

| 症状 | 根因 | 解决 |
| :--- | :--- | :--- |
| `Using kotlin.sourceSets DSL ... is not allowed` | AGP 9.3 内置 Kotlin 默认拦截 KSP 生成目录 | 保持 `android.disallowKotlinSourceSets=false` |
| `jlink` / `JdkImageTransform` 崩溃 | 环境把 `JAVA_HOME` 指向了 JBR 而非完整 JDK 21 | 用 `JAVA_HOME` 指向完整 JDK 21（见第 3 步） |
| `graph.bin 拒绝访问`（GBK 乱码） | Windows Defender 锁 `build/` 目录 | 排除目录 + `gradlew --stop` + clean，**不要改代码** |
| 抠图崩溃且提示模型异常 | LFS 指针未拉取 | `git lfs pull` |
| `dl.google.com` 超时 | 依赖源网络问题 | `settings.gradle.kts` 已前置阿里云镜像，不通时自动回退官方源 |
| release APK 装不上 | 未签名（环境变量缺失） | 跑 `gradlew signingReport` 看 `Config` 列 |

---

---

## 相关文档

- [`RELEASE.md`](RELEASE.md) · release 签名、体积优化、GitHub Releases 发布流程
- [`FEATURES.md`](FEATURES.md) · 完整功能清单
