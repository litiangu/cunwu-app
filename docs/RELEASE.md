# 发布流程 · Release 构建与 GitHub Releases 分发

> 面向维护者。目标读者是「想把改动发出去的人」，不涉及日常开发。
> 本文件与 `README.md` 互补：README 面向使用者与贡献者，本文件面向发版。

---

## 0. 先理解三条硬约束

这三条决定了整个流程的形状，动之前必须知道。

| 约束 | 数值 | 出处 |
| :--- | :--- | :--- |
| GitHub 仓库单文件硬上限 | **100 MiB** | 超过直接 push 被拒 |
| GitHub Releases 单文件上限 | 受 LFS 配额约束（远超 100 MiB） | 无仓库总体积限制 |
| ormbg 模型体积 | **168 MB**（`noCompress`，不可压缩） | `app/build.gradle.kts` 的 `androidResources` |

**推论：APK 绝对不能进 git 仓库。** debug APK 已经 254 MB，是 100 MiB 的 2.5 倍。
唯一正确的位置是 **GitHub Releases 附件**。

### 仓库体积健康度

当前 `.git` = **327 MB**（`.git/objects` 158 MB + `.git/lfs` 169 MB）。
GitHub 官方建议仓库保持在 **1 GB 以内**，5 GB 为硬性劝退线。
LFS 的 169 MB 是必要成本（模型随包分发，用户 clone 即得，无需额外下载动作），
但别再往仓库里塞二进制了。

---

## 1. 生成签名密钥（一次性）

### 1.1 生成 keystore

在你本机执行（PowerShell 或 Git Bash 均可）：

```bash
# ⚠️ 密钥必须生成在工程目录之外，绝不要落在代码仓库里
keytool -genkeypair -v \
  -keystore C:\Users\GU\keys\cunwu-release.p12 \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -alias cunwu
```

提示的 `store password` 与 `key password` 建议设成同一个值。
`validity 10000` 天约 27 年，避免中途过期导致用户无法升级。

**密钥放在工程目录里是被 gitignore 挡住的，但仍属高风险**：
哪天 `.gitignore` 被改、或有人用 `git add -f`，签名权就公开了。
统一放`C:\Users\GU\keys\`，靠「不在仓库目录内」这层物理隔离兜底。

### 1.2 备好四个环境变量

`app/build.gradle.kts` 的 `signingConfigs` 从环境变量读取，四个都要：

| 变量名 | 含义 | 示例（Windows PowerShell） |
| :--- | :--- | :--- |
| `CUNWU_KEYSTORE_PATH` | keystore 绝对路径 | `C:\Users\GU\keys\cunwu-release.p12` |
| `CUNWU_KEYSTORE_PASSWORD` | store 口令 | `$env:CUNWU_KEYSTORE_PASSWORD = "..."` |
| `CUNWU_KEY_ALIAS` | 别名 | `$env:CUNWU_KEY_ALIAS = "cunwu"` |
| `CUNWU_KEY_PASSWORD` | key 口令 | `$env:CUNWU_KEY_PASSWORD = "..."` |

持久化到用户级环境变量（只需一次，之后所有终端可用）：

```powershell
[Environment]::SetEnvironmentVariable("CUNWU_KEYSTORE_PATH", "C:\Users\GU\keys\cunwu-release.p12", "User")
[Environment]::SetEnvironmentVariable("CUNWU_KEY_ALIAS", "cunwu", "User")
# 口令类变量同样用 SetEnvironmentVariable("...", "...", "User")，
# 但请确认这台机器的磁盘加密与账户安全
```

> 🔐 **不要**把口令写进 `gradle.properties`、不要提交 `keystore.properties`、不要贴进 Issue。
> 密钥泄露意味着任何人都能伪造你的应用更新。丢失密钥则无法为已安装用户发布升级，
> 届时只能改包名重新发布。

### 1.3 ⚠️ Android Studio 里签名不生效（环境块冻结）

**症状**：外部 PowerShell 跑 `signingReport` 显示 `Config: release`，
但 Android Studio 内置终端跑同一个命令显示 `Config: none`。

**根因**：Windows 环境变量分两层存储：

| 层 | 位置 | 谁读它 |
| :--- | :--- | :--- |
| 注册表 | `HKCU\Environment` | **新启动**的进程 |
| 进程内存 | 每个进程自己的环境块 | 只有它自己 |

`SetEnvironmentVariable(..., "User")` 只写注册表并广播通知，
**已开着的进程不会更新自己的环境块**。Android Studio 的内置终端与 Run 按钮
都是从 **IDE 进程**派生的，而 IDE 启动那一刻的环境块已经冻结。
所以只要 IDE 比你设置变量的时间早，就拿不到新值。

**解决**

| 做法 | 说明 |
| :--- | :--- |
| **重启 Android Studio** | 最直接。IDE 重启后重读注册表，变量就位 |
| 用外部 PowerShell 跑构建 | `assembleRelease` / `signingReport` 都可，不依赖 IDE |
| 当前终端临时设 | `$env:CUNWU_KEYSTORE_PATH = "..."`，只对该窗口有效 |

> 💡 排查口诀：**外部终端正常 + IDE 里失败 = 环境块冻结，重启 IDE。**
> 两处都失败才是变量没持久化，去查 §1.2。

`app/build.gradle.kts` 的 `signingConfigs` 已加诊断输出：变量缺失时会在
Gradle 配置阶段打印缺失项名，并提示上面两条排查路径。

---

## 2. 构建 release APK

```bash
gradlew.bat clean assembleRelease      # Windows
./gradlew clean assembleRelease       # macOS / Linux
```

产物：`app/build/outputs/apk/release/app-release.apk`

### 环境变量缺失时的行为

`signingConfigs.release` 只在四个变量**齐备**时才挂上签名。
缺任一项 → release 走未签名分支，产物装不上（Android 拒绝安装未签名 APK）。
这是刻意设计：让还没配密钥的贡献者能跑通构建，不在 Gradle 配置阶段就崩。

判断构建是否成功签名，看 `signingReport`：

```bash
gradlew.bat signingReport
```

`Variant: release` 一行的 `Config:` 列应显示 `CUNWU_KEYSTORE_PATH` 指向的文件，
若显示 `<no signing config>` 说明环境变量没生效。

### 已启用的体积优化

工程用 **AGP 9.3.1**，走的是新 DSL：

```kotlin
optimization {
    enable = true       // 同时开启代码优化（混淆 + 收缩）与资源裁剪
}

ndk {
    abiFilters += listOf("arm64-v8a")   // 只保留主流真机 ABI
}
```

`optimization { enable = true }` 会自动内置 `proguard-android-optimize.txt` 的等价规则，
**不需要**再手写 `isMinifyEnabled` / `isShrinkResources` / `proguardFiles`。
keep 规则放在 `app/src/main/keepRules/*.keep`，AGP 会把该目录下所有 `.keep` 文件合并后传给 R8。

各部分瘦身效果（**2026-10-16 实测**，不是估算）：

| 组成 | debug | release 实测 | 变化 |
| :--- | ---: | ---: | ---: |
| `assets/ormbg.tflite` | 168.1 MB | **168.1 MB** | +0% |
| dex 合计 | 66.5 MB | **3.3 MB** | **−95%** |
| native lib | 17.2 MB | 7.4 MB | −57% |
| resources | 0.4 MB | 0.2 MB | −50% |
| 清单 / 签名等 | 0.8 MB | 0.4 MB | −51% |
| **合计** | **254.0 MB** | **179.2 MB** | **−29%** |

减掉的 74 MB 全部来自 dex 与 native lib。模型那 168 MB 一分未动，
`noCompress` 是 LiteRT `mmap` 的硬要求，动不了。

**总占比重新洗牌**：优化前模型占 APK 的 66%，优化后占 94%。
这个比例决定了后续所有体积工作的天花板，除非换 int8 模型或上 Asset Delivery。

### 构建耗时

`assembleRelease` 首次约 **4 分 26 秒**（50 个 task 全执行，含 R8）。
`assembleDebug` 约 1 分钟。CI 上建议缓存 Gradle 与 R8 产物。

### debug 包名后缀

`buildTypes.debug` 加了 `applicationIdSuffix = ".debug"` 与
`versionNameSuffix = "-debug"`。效果是 debug 包名为 `com.litiangu.wuji.debug`，
可与 release 并存安装，调试不覆盖正式版。

代价：**没有后缀的截图文件名与包名对不上**。若你之前按 `com.litiangu.wuji` 排查过
logcat 或抓过 `adb pull`，需要改成 `.debug`。

### 已知无害告警

`assembleRelease` 会输出三类 warning，都不影响产物与运行：

| 告警 | 出处 | 处置 |
| :--- | :--- | :--- |
| `Unable to strip ... libLiteRt.so` | LiteRT 的 `.so` 未附符号表 | 无法处理，打包原样带上即可 |
| `Icons.Filled.KeyboardArrowRight is deprecated` | `AnalysisScreen.kt:1473` | 可直接改 `Icons.AutoMirrored.Filled.KeyboardArrowRight`，已验证当前 Compose 版本可用 |
| `centerAlignedTopAppBarColors is deprecated` | 7 处（About / Backup / BudgetAnchor / ProfileEdit / Settings / Trash） | 可直接改 `TopAppBarDefaults.topAppBarColors`，已验证当前 Compose 版本可用 |

> 📌 **`NextStep.md` 里「`Icons.AutoMirrored.Filled.KeyboardArrowRight` 在当前 Compose
> 版本不存在」这条记录已过时。** 现工程 `SettingsScreen.kt:437` 已在用该 API 且能正常编译
> （同属 `material-icons-extended`，BOM 已升到 2026.02.01）。
> `TopAppBarDefaults.topAppBarColors` 同理，`HomeScreen.kt:299` 已在用。
> 清理这批 deprecated 告警是安全的独立小步，建议单开一步处理。

另有 `TrashScreen.kt:350` 一条 `Condition is always 'true'`，同样属独立清理项。

---

## 3. 发布到 GitHub Releases

### 3.1 打 tag

沿用项目里程碑命名习惯：

```bash
git tag -a v1.0.0 -m "v1.0.0 首个正式版本"
git push --follow-tags     # 或分两次：git push; git push --tags
```

> 沙箱无 push 凭据，本步骤请在你的本机执行。

### 3.2 建 Release 并挂 APK

> 📋 **给发布者的逐步操作指引（浏览器版，无需命令行）**

**准备**

- APK 路径：`E:\AndroidStudioProjects\Wuji\app\build\outputs\apk\release\app-release.apk`（179 MB）
- 发布前先确认远端地址正确：
  ```bash
  git remote -v   # 应指向 git@github.com:litiangu/cunwu-app.git
  ```

**第 1 步 · 提交并推送**

```bash
git add -A
git status                     # 确认没有密钥文件混进来
git commit -m "docs: 完善 README 与发布流程文档"
git push
```

> 🔐 `git status` 里**不应出现** `*.p12` / `*.jks`。`.gitignore` 已覆盖，
> 但推送前扫一眼是值得的习惯。

**第 2 步 · 打 tag**

```bash
git tag -a v1.0.0 -m "v1.0.0 首个正式版本"
git push origin v1.0.0
```

> 推送前关掉 WattToolkit / SteamTools。
> 仓库曾用名 `wuji-app-demo`，已更名为 `cunwu-app`。GitHub 保留旧地址的 301 重定向，
> 旧 remote 仍能推，但建议改到新地址：
> ```bash
> git remote set-url origin git@github.com:litiangu/cunwu-app.git
> git remote -v   # 确认已指向 cunwu-app
> ```
>
> ⚠️ **`git status` 可能长期显示 `ahead N`**，那是本地 `origin/main` 引用陈旧造成的假象。
> 用 `git fetch origin` 刷新，或直接用 `git ls-remote origin main` 核对远端真实 SHA。
> 强推时 `--force-with-lease` 会因陈旧引用报 `stale info`，改用显式期望值：
> ```bash
> SHA=$(git ls-remote origin main | cut -f1)
> git push --force-with-lease=main:$SHA origin main
> ```

**第 3 步 · 建 Release**

浏览器打开 <https://github.com/litiangu/cunwu-app/releases/new>：

| 表单字段 | 填什么 |
| :--- | :--- |
| **Choose a tag** | 选 `v1.0.0`（或点 `create a new tag`） |
| **Target** | `main` |
| **Release title** | `v1.0.0` |
| **Release notes** | 见下方模板 |

**第 4 步 · 上传 APK**

把 `app-release.apk` 拖进页面底部的 `Attach files by dragging & dropping` 区域。
179 MB 上传需要几分钟，**等进度条走完再点 Publish**，中途刷新会丢失。

> ⏱ GitHub Releases 单文件上限受 LFS 配额约束，179 MB 远在限制内。

**第 5 步 · 验证**

```bash
# 确认远端 tag 存在
git ls-remote --tags origin
```

浏览器打开仓库首页，确认 README 的「⬇️ 下载 APK」按钮能跳到 Release 页面。

### 3.2.1 Release notes 模板

```markdown
## v1.0.0 · 首个正式版本

本地物品管理 App，用健康分呈现物品的使用与性价比。

### 安装
下载 `app-release.apk`，需 Android 14（API 34）或更高版本。
体积 179 MB，其中端侧抠图模型占 168 MB。

### 亮点
- 健康分二维模型：利用率 55% + 价值健康度 45%，四档评级
- 端侧智能抠图：ormbg（ISNet, Apache-2.0）纯 CPU 推理，无网络权限
- 首页三视图：时间轴 / 瀑布流 / 陈列馆
- 全部数据本地存储，无账号、无云端、无 INTERNET 权限
- 含图 zip 备份与还原，30 天回收站自动清理

### 校验
签名 SHA-256：
`88:DE:16:42:4C:A9:42:0F:4D:03:E7:AD:C3:4B:1E:93:DC:55:0A:9F:0C:F6:6B:7D:D:6C:0B:E3:51:8F:84:A7:BF`

### 许可
Apache License 2.0
```

命令行等价做法：

```bash
gh release create v1.0.0 \
  app/build/outputs/apk/release/app-release.apk \
  --title "v1.0.0" \
  --notes-file release-notes.md
```

### 3.3 校验发布物

`apksigner` 与 `adb` 一样**不在 PATH 里**，PowerShell 要用完整路径调用：

```powershell
$sig = "C:\Users\GU\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat"
$apk = "E:\AndroidStudioProjects\Wuji\app\build\outputs\apk\release\app-release.apk"

& $sig verify --verbose $apk
& $sig verify --print-certs $apk    # 只看指纹时用这个

# 确认包名与版本
& "C:\Users\GU\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt2.exe" dump badging $apk | Select-Object -First 5
```

`build-tools` 版本号随 SDK 更新变化，用 `ls C:\Users\GU\AppData\Local\Android\Sdk\build-tools\`
确认当前实际版本再替换。

本工程的签名指纹（2026-10-16 首次生成，**永久有效，务必妥善保存**）：

```
Store:  C:\Users\GU\keys\cunwu-release.p12（**工程目录外**，2026-10-16 迁出）
Alias:  cunwu
SHA-1:  02:CD:35:79:42:E0:F6:67:8E:50:4F:FB:31:94:62:0B:F1:A4:70:D6
SHA-256: 88:DE:16:42:4C:A9:42:0F:4D:03:E7:AD:C3:4B:1E:93:DC:
         55:0A:9F:0C:F6:6B:7D:D:6C:0B:E3:51:8F:84:A7:BF
有效期至: 2054 年
```

这三个指纹是应用的永久身份。**任何版本都必须用同一份密钥签名**，
换了指纹 Android 会判定为另一个应用，已安装用户将无法收到更新。

---

## 4. 实测数据回填

### v1.1.0 构建记录（2026-10-16）

| 项 | 实测值 |
| :--- | :--- |
| versionCode / versionName | **2** / **1.1**（`aapt2 dump badging` 核实） |
| 构建耗时 | **2 分 24 秒**（50 tasks，48 executed，含 R8 + lintVital） |
| 产物体积 | **187,946,097 字节（179.2 MB）** |
| R8 | `minifyReleaseWithR8` + `shrinkResources` 均已执行 |

> ✅ **Step 59 的构建改动验证通过**：删除 `org.gradle.java.home` 后，
> `clean assembleRelease` 一次跑通，无 jlink/JdkImageTransform 问题。
> foojay 自动解析 JDK 生效，本机无需额外配置。

### v1.0.0 实测数据（2026-10-16 首发）

| 项 | 实测值 | 记录日期 |
| :--- | :--- | :--- |
| release APK 体积 | **179.2 MB**（debug 254.0 MB，−29%） | 2026-10-16 |
| 构建耗时 | 4 分 26 秒（50 tasks，含 R8） | 2026-10-16 |
| 签名配置 | `Config: release`，SHA-1/256 已记录 | 2026-10-16 |
| `apksigner verify` | **`Verifies` 通过**，1 个签名者 | 2026-10-16 |
| 签名方案 | **v3 生效**（v1/v2 因 minSdk 34 被 AGP 自动跳过） | 2026-10-16 |
| ABI | 仅 `arm64-v8a`（abiFilters 生效） | 2026-10-16 |
| dex 瘦身 | 66.5 MB → 3.3 MB（−95%） | 2026-10-16 |
| 真机安装 | **arm64 真机安装成功** | 2026-10-16 |
| 冷启动 Room 回归 | **通过**，进程存活零崩溃，引导页正常渲染 | 2026-10-16 |
| ormbg 模型完整性 | **SHA-256 与 LFS 一致**，`compress_type=0`（未压缩） | 2026-10-16 |
| 抠图功能回归 | **通过**（arm64 真机，LiteRT 加载正常） | 2026-10-16 |
| 备份还原回归 | **通过**（arm64 真机，backup.json 反序列化正常） | 2026-10-16 |

> ✅ **R8 三路径回归全部通过（2026-10-16 arm64 真机）。**
> `rules.keep` 的 keep 规则经真机验证有效，release 构建可安全发布。

> ⚠️ **v1.1.0 的 R8 三路径回归尚未做**，发布前必须补：
> 抠图（LiteRT 加载 ormbg）· 备份还原（serialization）· 冷启动（Room 查询）。
> R8 的坑全在运行期，编译期一声不吭。

### 2026-10-16 模拟器验证记录

在 x86_64 模拟器（`sdk_gphone64_x86_64`）上完成的部分验证：

| 项 | 结果 | 说明 |
| :--- | :--- | :--- |
| `adb install` | `Success` | 旧包签名冲突需先卸载，见下 |
| 冷启动 | 进程存活 `22409`，零 `FATAL` | Room 初始化与查询未被 R8 裁掉 |
| Compose 渲染 | 引导页 Logo / 文案 / CTA 全部正常 | 资源裁剪未误删，UI 包未被破坏 |
| 抠图 | 无法在此验证 | APK 只含 arm64-v8a 原生库，模拟器缺 `libLiteRt.so` |
| 备份还原 | 无法在此验证 | release 包非 debuggable，`run-as` 被拒 |

**剩余两条已在 arm64 真机补齐（2026-10-16）：**

| 项 | 结果 |
| :--- | :--- |
| 真机安装 | 成功 |
| 抠图 | **正常**（LiteRT 加载 ormbg 模型 + 后处理流水线无异常） |
| 备份还原 | **正常**（`backup.json` 反序列化、图片重映射均通过） |
| 冷启动 | **正常** |

**结论：R8 三路径回归全部通过，`rules.keep` 的 keep 规则经真机验证有效，release 可安全发布。**

### 两个必须知道的安装陷阱

**一、签名冲突**

设备上装过旧 debug 包（`com.litiangu.wuji`，debug 签名）时，装 release 会报：

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.litiangu.wuji
signatures do not match newer version; ignoring!
```

**不同签名不被视为同一个 App，覆盖安装必然失败。** 两条出路：

```bash
# 方案 A：卸载旧包（数据全丢）
adb uninstall com.litiangu.wuji

# 方案 B：先导出数据再卸载
adb shell "am force-stop com.litiangu.wuji"
adb shell "run-as com.litiangu.wuji tar cf - -C /data/data/com.litiangu.wuji databases" > databases.tar
adb shell "run-as com.litiangu.wuji tar cf - -C /data/data/com.litiangu.wuji files/images"   > images.tar
adb uninstall com.litiangu.wuji
```

> ⚠️ 导出数据库时**必须带上 `-wal` 文件**。本项目 Room 开了 WAL，
> 主库 `wuji.db` 可能只有 4 KB（仅 schema），真实数据全在 `wuji.db-wal` 里。
> `am force-stop` 会在导出前先把 WAL checkpoint 一遍，降低这个风险。
> `run-as` 只对 `debuggable=true` 的包有效，release 包会被拒。

> 🔴 **千万别用 `adb shell "cmd" > file` 导出二进制**
>
> ```bash
> # ❌ 错误写法：adb shell 的输出经过 Windows 控制台做字符转换，tar 流被截断
> adb shell "run-as <pkg> tar cf - -C /data/data/<pkg> files/images" > images.tar
>
> # ✅ 正确：exec-out 走管道，不过控制台
> adb exec-out "run-as <pkg> tar cf - -C /data/data/<pkg> files/images" > images.tar
>
> # ✅ 更稳：先落设备再 pull
> adb shell "run-as <pkg> tar cf /sdcard/bk.tar -C /data/data/<pkg> files"
> adb pull /sdcard/bk.tar ./bk.tar
> adb shell rm /sdcard/bk.tar
> ```
>
> 2026-10-16 实测踩中这个坑：33 张图只导出 1 张，`images.tar` 显示 7.2 MB 但解出来只有 1 个文件。
> **导出后必须校验条目数**，`tarfile.getnames()` 数一遍，别只看文件大小：
>
> ```python
> import tarfile
> with tarfile.open("images.tar") as t:
>     print(len(t.getnames()), "个条目")   # 对比设备上 `ls files/images | wc -l`
> ```

**二、ABI 不匹配**

`abiFilters = ["arm64-v8a"]` 生效后 release APK 不含 x86_64 原生库。
好消息是 **安装不会被拦截**（Android 允许缺失 `.so`，只在该库被加载时报错），
所以模拟器上能装能跑，只是 LiteRT 相关的抠图功能不可用。

### 签名方案说明

`apksigner verify --verbose` 的权威输出：

```
Verifies
Verified using v1 scheme (JAR signing):      false
Verified using v2 scheme (APK Signature v2):  false
Verified using v3 scheme (APK Signature v3):  true
Number of signers: 1
```

**只 v3 生效是正确行为，不是配置错误。** v1 只在 Android 7.0 以下需要，
v2 只在 Android 11.0 以下需要，`minSdk = 34` 两者都用不上，AGP 会自动跳过。

> ⚠️ **别被 ZIP 里的 `META-INF/CERT.RSA` 误导。** v1 签名的产物文件确实存在，
> 但那是 AGP 为兼容性保留的残留字节，不代表 v1 校验能通过。
> 判断签名是否有效，只认 `apksigner verify` 的输出。

**R8 的真实风险在运行期，不在编译期。** 首次 release 构建务必回归这三条路径：

1. **抠图**（LiteRT 加载 ormbg 模型 + 后处理流水线）
2. **备份与还原**（kotlinx.serialization 反序列化 `backup.json`）
3. **Room 查询**（应用冷启动即触发数据库读取）

`app/src/main/keepRules/rules.keep` 已针对这三处写了 keep 规则，但只有在真机上跑过才算验证通过。

> 📌 **`rules.keep` 里有两条兜底规则是为「不崩」服务的**，它们会让 R8 的裁剪变保守：
> `-keep class com.litiangu.wuji.ui.** { *; }` 保住了整个 UI 包，
> `-keep class com.litiangu.wuji.data.db.** { *; }` 保住了整个 db 包。
> 这解释了为什么 dex 只降到 3.3 MB 而非更低。
>
> **这两条已经过 arm64 真机验证有效**（2026-10-16，抠图 / 备份还原 / 冷启动全通过）。
> 想换更小体积可以逐步收窄它们，但**每次收窄后必须重新跑完那三条回归**。
>
> 收窄思路：先去掉 `ui.**` 整包保留，让 R8 自己判断（Compose 编译器生成的规则通常够用）；
> 跑通三条回归后再动 `data.db.**`。若某条回归挂了，对应加回精确到类的 keep 规则即可。

---

## 5. 待办清单

**已完成（2026-10-16）**

- [x] 本机生成 `cunwu-release.p12`（4390 字节，PKCS#12，RSA 4096，有效期至 2054）
- [x] 四个环境变量生效，`signingReport` 显示 `Config: release`
- [x] `assembleRelease` 成功，**179.2 MB**，`apksigner verify` 通过（v3 方案）
- [x] 真实数据已回填第 4 节
- [x] 旧 debug 包数据已导出到 `E:\app demo\wuji_backup_20261016`（db + datastore + 图片）
- [x] 模拟器安装 + 冷启动 + Compose 渲染验证通过（Room 未被 R8 裁掉）
- [x] **arm64 真机安装 release 包成功**
- [x] **R8 三路径回归全通过**：抠图正常 / 备份还原正常 / 冷启动正常

**待完成**

- [ ] 环境变量持久化到 User 级（当前仅在单个 PowerShell 会话设过）
- [ ] 打 `v1.0.0` tag 并推
- [ ] 建 Release 挂 APK，写 release notes
- [x] **真实截图已拍摄并替换占位图**（9 张真机截图，Android 14，1080×2400，缩至宽 720，共 2.0 MB）
- [ ] README 路线图勾掉「Release 真机回归」
- [ ] （可选）收窄 `rules.keep` 的两条兜底 keep 规则，换取更小体积

**已知遗留（非阻塞）**

- [ ] 清理 7 处 `centerAlignedTopAppBarColors` deprecated
- [ ] 清理 `AnalysisScreen.kt:1473` 的 `KeyboardArrowRight` deprecated
- [ ] 清理 `TrashScreen.kt:350` 的 `Condition is always 'true'`

---

## 6. 为什么 APK 不进仓库

留给未来想「优化一下」的人：

| 做法 | 后果 |
| :--- | :--- |
| 把 APK 提交进 git | 254 MB > 100 MiB，**push 被 GitHub 直接拒绝** |
| 用 LFS 存 APK | 能过，但 `.git` 从 327 MB 涨到 580 MB，逼近 1 GB 警戒线，且每次发版都产生一个永久历史版本，clone 变慢 |
| 改文件名绕过检测 | 无效，GitHub 按字节数判定 |
| 提交 `debug-jks` 方便他人构建 | **密钥公开等于应用签名权公开**，任何人可伪造覆盖安装 |

唯一正确解是 Releases 附件：仓库保持源码 + 模型的干净状态，APK 走独立的分发通道。