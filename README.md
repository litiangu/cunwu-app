<div align="center">

<img src="docs/banner.svg" alt="存物 Cunwu · 存下此刻，物有所值" width="100%" />

# 存物 Cunwu

**存下此刻，物有所值**

本地物品管理 · 以健康分呈现物品的使用与性价比 · 辅助断舍离决策

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2026.02-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-757575?style=flat-square&logo=materialdesign&logoColor=white)](https://m3.material.io)
[![Room](https://img.shields.io/badge/Room-2.8.4-3DDC84?style=flat-square&logo=android&logoColor=black)](https://developer.android.com/training/data-storage/room)
[![LiteRT](https://img.shields.io/badge/LiteRT-2.1.0-4285F4?style=flat-square&logo=google&logoColor=white)](https://ai.google.dev/edge/litert)
[![Gradle](https://img.shields.io/badge/Gradle-9.5.0-02303A?style=flat-square&logo=gradle&logoColor=white)](https://gradle.org)
[![AGP](https://img.shields.io/badge/AGP-9.3.1-3DDC84?style=flat-square&logo=androidstudio&logoColor=black)](https://developer.android.com/build)
[![JDK](https://img.shields.io/badge/JDK-21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://openjdk.org)
[![minSdk](https://img.shields.io/badge/minSdk-34-34A853?style=flat-square)](https://developer.android.com/about/versions/14)
[![targetSdk](https://img.shields.io/badge/targetSdk-36-34A853?style=flat-square)](https://developer.android.com/about/versions/16)
[![License](https://img.shields.io/badge/License-Apache%202.0-D22128?style=flat-square)](LICENSE)

[![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=flat-square&logo=android&logoColor=black)](https://developer.android.com)
[![Architecture](https://img.shields.io/badge/architecture-MVVM%20%2B%20Compose-FF5500?style=flat-square)](docs/ARCHITECTURE.md)
[![Privacy](https://img.shields.io/badge/privacy-no%20INTERNET%20permission-34A853?style=flat-square)](#-隐私承诺)
[![MinSdk](https://img.shields.io/badge/minSdk-34%20(Android%2014%2B)-FF5500?style=flat-square)](docs/BUILD.md#环境要求)

<br/>

## 📥 获取应用

<table>
<tr>
<td align="center" width="34%">
<a href="https://github.com/litiangu/cunwu-app/releases"><b>⬇️ 下载 APK</b></a><br/>
<sub>Releases · 需 Android 14+</sub>
</td>
<td align="center" width="33%">
<a href="docs/BUILD.md"><b>🔧 源码构建</b></a><br/>
<sub>需 JDK 21 + Git LFS</sub>
</td>
<td align="center" width="33%">
<a href="#-隐私承诺"><b>🔒 隐私承诺</b></a><br/>
<sub>无账号 · 无网络权限</sub>
</td>
</tr>
</table>

> 📦 下载 [Releases 页](https://github.com/litiangu/cunwu-app/releases) 的 `app-release.apk`，
> 或按[构建指南](docs/BUILD.md)从源码编译。
>
> ⚠️ APK 体积约 **179 MB**（release，已签名）/ **254 MB**（debug），
> 其中 ormbg 端侧模型独占 **168 MB**（占 release 的 94%）。首次下载请预留时间与磁盘空间。

</div>

---

## 📸 界面预览

<table>
<tr>
<td align="center" width="33%"><b>首页 · 瀑布流</b><br><sub>资产概览 + 抠图贴纸</sub></td>
<td align="center" width="33%"><b>分析页</b><br><sub>健康分同心环 + 资产结构</sub></td>
<td align="center" width="33%"><b>物品详情</b><br><sub>日均成本 / 陪伴天数</sub></td>
</tr>
<tr>
<td align="center"><img src="docs/screenshots/01-home.png" alt="首页瀑布流" width="90%" /></td>
<td align="center"><img src="docs/screenshots/02-analysis.png" alt="分析页" width="90%" /></td>
<td align="center"><img src="docs/screenshots/03-item-detail.png" alt="物品详情" width="90%" /></td>
</tr>
<tr>
<td align="center"><b>时间轴</b><br><sub>按购入月份分组</sub></td>
<td align="center"><b>陈列馆</b><br><sub>紧凑网格速览</sub></td>
<td align="center"><b>三视图切换</b><br><sub>一键切换布局</sub></td>
</tr>
<tr>
<td align="center"><img src="docs/screenshots/04-view-timeline.png" alt="时间轴视图" width="90%" /></td>
<td align="center"><img src="docs/screenshots/05-view-gallery.png" alt="陈列馆视图" width="90%" /></td>
<td align="center"><img src="docs/screenshots/06-view-switch.png" alt="视图切换" width="90%" /></td>
</tr>
<tr>
<td align="center"><b>深色模式 · 首页</b><br><sub>白边贴纸在黑底上轮廓清晰</sub></td>
<td align="center"><b>深色模式 · 设置</b><br><sub>自研双 ColorScheme</sub></td>
<td align="center"><b>设置页</b><br><sub>隐私 / 震动 / 预算</sub></td>
</tr>
<tr>
<td align="center"><img src="docs/screenshots/07-dark-home.png" alt="深色首页" width="90%" /></td>
<td align="center"><img src="docs/screenshots/08-dark-settings.png" alt="深色设置" width="90%" /></td>
<td align="center"><img src="docs/screenshots/09-settings.png" alt="设置页" width="90%" /></td>
</tr>
</table>

> 📱 真机截图（Android 14，1080×2400），存于 `docs/screenshots/`。
> 数据为演示用测试数据，物品图片经端侧 ormbg 抠图处理。

---

## 📖 简介

### English Summary

**Cunwu** is a local-first Android app for cataloguing the things you own, built around one question: *is my stuff actually being used?*

Every item you record feeds a two-dimensional **Health Score**:

```
score = 0.55 × D1(利用率) + 0.45 × D2(价值健康度)
```

- **D1 Utilization** · in-use items ÷ owned items. Idle objects drag it down.
- **D2 Value Health** · your daily budget anchor ÷ the aggregate daily cost of holding everything. A coffee maker kept for six years is cheap per day; one replaced yearly is not.

Backgrounds are removed **fully on-device** with `ormbg` (an Apache-2.0 ISNet matting model) running on Google LiteRT. No account, no cloud, no `INTERNET` permission, no analytics. Your data never leaves the device.

### 中文简介

家里东西越买越多，却说不清哪些在用、哪些在吃灰。**存物** 让你把每件物品记下来，然后用一张**健康分**回答那个真正的问题：**这些钱花得值不值？**

健康分由两个维度构成：

| 维度 | 权重 | 含义 |
| :--- | :---: | :--- |
| **D1 利用率** | 55% | 使用中 ÷ 在持（已售出不计入分母）。闲置越多，分越低 |
| **D2 价值健康度** | 45% | `100 × min(1, 每日预算锚点 ÷ 聚合日均持有成本)`。陪伴越久，日均越低，分越高 |

拍下物品照片，App 会在**本机**用 `ormbg`（ISNet 架构，Apache-2.0）抠掉背景并做贴纸化处理，无需联网、无需上传。

所有数据只落在本机：没有账号，没有云端，`AndroidManifest.xml` 里**没有声明 `INTERNET` 权限**。这是一个可以从代码层面验证的承诺。

---

---


## 🚀 使用示例

### 示例 1 · 记录第一件物品

1. 首次启动进入**引导页**，点「开始你的第一个物品记录」
2. 选择分类（数码 / 电器 / 家具 / 工具 / 运动 / 交通）或自定义新建
3. 填写名称、购入价格、购买日期（内置中文日期选择器）
4. 设置状态：使用中 / 未使用 / 已售出
5. 可选填写附加费用（配件、维修）与二手收益金额
6. 点「保存」，物品立刻出现在首页

### 示例 2 · 用智能抠图给旧物拍张证件照

1. 在物品表单点图片区 → 拍照或从相册选
2. App 在**本机**用 LiteRT 跑 ormbg 模型推理（1024×1024 输入，CPU）
3. 后处理流水线依次执行：二值化 → 形态学 → 背景先验 → 填洞 → 最大连通域 → 边缘内缩
4. 输出贴纸化 PNG：白色描边 + 边缘投影，深浅背景下都清晰
5. 图片存进应用私有目录，卸载即清除

> 抠图结果持久化在数据库的图片路径字段里。**升级算法后旧图不会自动重算**，需要手动重新抠图。

### 示例 3 · 读懂健康分

假设你库里有 12 件物品：

- 9 件使用中、2 件未使用、1 件已售出

**D1 利用率** = 9 ÷ 11（在持 11 件，已售出不计入分母）= **81.8 分**

**D2 价值健康度**，假设这些物品净投入 ¥8,000，累计陪伴 3,000 天：

- 聚合日均持有成本 = 8000 ÷ 3000 = ¥2.67 / 天
- 每日预算锚点 ¥100 → D2 = 100 × min(1, 100 ÷ 2.67) = **100 分**（封顶）

**总分** = 0.55 × 81.8 + 0.45 × 100 = **90.0 分 → 优「物尽其用」**

把预算锚点下调到 ¥2 / 天试试，D2 会立刻掉下来。这就是健康分想让你看见的：**日均成本比总价格更诚实**。

### 示例 4 · 用健康分驱动断舍离

1. 进**分析页**，首屏是健康卡（同心多环）与资产卡
2. 健康环偏红（差「该唤醒了」）→ 看下方**物品排行榜**的「日均成本最高」
3. 找到那件日均 ¥80 的闲置物 → 回首页长按搜索定位
4. 判定它是淘汰还是转卖 → 填「二手收益金额」，状态改「已售出」
5. 资产卡的「已回流资金」立刻上涨，**D1 不受拖累**（已售出不计入分母）
6. 总资产 = Σ(购入 + 附加) − Σ(变现) 同步下降，但**性价比分数上升**

### 示例 5 · 三视图找到那件旧物

首页右上角切换视图：

| 模式 | 适合场景 |
| :--- | :--- |
| **时间轴** | 回忆与盘点「这个月买了什么」 |
| **瀑布流** | 视觉浏览，看图片找某件东西 |
| **陈列馆** | 快速扫一眼整体持有面貌 |

配合搜索框输入名称片段，或点筛选按钮按状态（使用中 / 未使用 / 已售出）与排序（日期 / 金额，升 / 降）缩小范围。

### 示例 6 · 换机前备份与还原

1. 进**设置 → 数据备份**
2. 「创建新备份」→ 生成 `.zip`（含 `backup.json` + `images/` 全量图片）
3. 通过「分享文件」发给云盘，或「保存到手机」落到 Downloads
4. 换机后装好 App，进入同一页面
5. 「恢复数据」→ 选 zip → 确认覆盖（事务写入，失败不落脏数据）
6. 备份提醒阈值可选 3 / 7 / 14 天，超期时首页显示提示行

### 示例 7 · 公开展示时的隐私模式

1. **设置 → 隐私模式** 打开
2. 首页与分析页的所有金额替换为 `****`
3. 柱状图高度与占比仍按真实值计算，视觉比例不失真
4. 适合投屏演示、录屏分享、咖啡馆展示

---

---


## 🔒 隐私承诺

1. **数据本地化**。所有物品信息、图片、预算与偏好仅存于本机数据库与 DataStore，不上传任何服务器。
2. **无网络权限**。`AndroidManifest.xml` 未声明 `INTERNET`，应用在系统层面就无法联网。
3. **无第三方账号**。不要求注册，不与任何人共享数据。
4. **AI 推理在本机**。ormbg 模型随包分发，LiteRT 在设备 CPU 上运行，抠图过程零网络。
5. **数据自管**。备份由你自己创建、自己保管，卸载应用即彻底删除。

---

---


## 📄 许可证

本项目采用 [Apache License 2.0](LICENSE)。

```
Copyright 2026 存物 Cunwu

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

### 第三方组件

| 组件 | 来源 | 协议 |
| :--- | :--- | :--- |
| Kotlin | JetBrains | Apache-2.0 |
| Jetpack Compose（Material 3 / UI / Activity） | Google | Apache-2.0 |
| AndroidX Core KTX / Lifecycle / Room / DataStore | Google | Apache-2.0 |
| Material Icons | Google | Apache-2.0 |
| Navigation Compose | Google | Apache-2.0 |
| LiteRT | Google | Apache-2.0 |
| kotlinx.serialization | JetBrains | Apache-2.0 |
| **ormbg**（ISNet 抠图模型） | schirrmacher / litert-community | **Apache-2.0** |

> 选择 ormbg 而非 RMBG-1.4 的原因很直接：RMBG-1.4 是**非商业许可**，开源项目无法合法分发；ormbg 是完全开放的 Apache-2.0 权重，是它的可商用替代方案。

---

## 📄 说明与免责声明

本应用按「原样」提供，用于物品记录与健康分参考，**不构成任何专业财务或法律建议**。健康分是对你记录数据的一种加权呈现，模型的合理性取决于你录入的信息是否准确。

---

## 📚 延伸阅读

| 文档 | 内容 |
| :--- | :--- |
| [核心特性](docs/FEATURES.md) | 完整功能清单：健康分引擎、抠图流水线、三视图、分析页组件 |
| [构建指南](docs/BUILD.md) | 环境要求、7 步源码构建、常见构建问题 |
| [架构与技术](docs/ARCHITECTURE.md) | 目录结构、技术栈选型、路线图 |
| [发布流程](docs/RELEASE.md) | release 签名、体积优化（254 → 179 MB）、GitHub Releases 发布 |
| [贡献指南](CONTRIBUTING.md) | 分支策略、提交规范、R8 注意事项、领域踩坑约定 |

---

<div align="center">

**存下此刻，物有所值**

[![Apache-2.0](https://img.shields.io/badge/License-Apache%202.0-D22128?style=flat-square)](LICENSE)
[![Made with Kotlin](https://img.shields.io/badge/Made%20with-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Local-first](https://img.shields.io/badge/Local--first-34A853?style=flat-square&logo=github&logoColor=white)](#-隐私承诺)

</div>
