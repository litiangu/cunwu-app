plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.litiangu.wuji"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.litiangu.wuji"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ---- [发布准备] release 签名 ----
    // 密钥与口令一律从环境变量读取，绝不写进本文件或提交进版本库。
    // 生成 keystore（一次性，在你本机执行）：
    //   keytool -genkeypair -v -keystore C:\Users\GU\keys\cunwu-release.p12 \
    //           -keyalg RSA -keysize 4096 -validity 10000 -alias cunwu
    //   ⚠️ 密钥务必生成在工程目录之外。放仓库里即使被 gitignore 挡住也不安全，
    //      靠「不在代码目录内」这层物理隔离兜底更可靠。
    //   （口令建议存进系统环境变量，不要留在 shell 历史里）
    // 需要的 4 个环境变量见下方 signingConfigs 注释。
    // 完整发布流程见 docs/RELEASE.md。
    signingConfigs {
        create("release") {
            val storePathEnv = System.getenv("CUNWU_KEYSTORE_PATH")
            val storePassEnv = System.getenv("CUNWU_KEYSTORE_PASSWORD")
            val keyAliasEnv = System.getenv("CUNWU_KEY_ALIAS")
            val keyPassEnv = System.getenv("CUNWU_KEY_PASSWORD")

            // 四个变量齐了才启用签名。缺任一项时 release 走「未签名」分支，
            // 方便还没配好密钥的贡献者本地跑通构建，不至于在 Gradle 配置阶段就崩。
            val signingReady = storePathEnv != null && storePassEnv != null &&
                keyAliasEnv != null && keyPassEnv != null

            if (!signingReady) {
                // 排查提示：Android Studio 内置终端与 Run 按钮读的是 IDE 进程的环境块，
                // 而 IDE 启动后环境即冻结。若在 IDE 里跑 signingReport 看到 Config: none、
                // 但外部 PowerShell 里正常，几乎都是这个原因 —— 重启 Android Studio 即可。
                // 用 println 而非 logger：android {} 作用域内没有 logger 实例，
                // 而 println 在 Gradle 配置阶段一定会输出到构建控制台。
                val missing = listOf(
                    "CUNWU_KEYSTORE_PATH" to storePathEnv,
                    "CUNWU_KEYSTORE_PASSWORD" to storePassEnv,
                    "CUNWU_KEY_ALIAS" to keyAliasEnv,
                    "CUNWU_KEY_PASSWORD" to keyPassEnv,
                ).filter { it.second == null }.map { it.first }
                println(
                    "[Cunwu] release 签名未启用，缺失环境变量：${missing.joinToString(", ")}\n" +
                        "  · 外部 PowerShell 里正常、IDE 里失败 → 重启 Android Studio（IDE 环境块启动时冻结）\n" +
                        "  · 两处都失败 → 变量未持久化，执行 docs/RELEASE.md §1.2"
                )
            }

            if (signingReady) {
                storeFile = file(storePathEnv)
                storePassword = storePassEnv
                keyAlias = keyAliasEnv
                keyPassword = keyPassEnv
                // minSdk = 34 时 AGP 自动跳过 v1/v2（只有 Android 7.0 / 11.0 以下才需要），
                // 实际只有 v3 生效。保留三行显式声明是无害的，说明签名意图。
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // debug 包名加 .debug 后缀，与 release 可并存安装，方便内部调试对比
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            // 签名：四个环境变量齐备时才挂上（判据与 signingConfigs 内保持一致）
            if (listOf("CUNWU_KEYSTORE_PATH", "CUNWU_KEYSTORE_PASSWORD",
                        "CUNWU_KEY_ALIAS", "CUNWU_KEY_PASSWORD").all {
                    System.getenv(it) != null
                }
            ) {
                signingConfig = signingConfigs.getByName("release")
            }

            // [发布准备] 体积优化。AGP 9.3 用新的 optimization DSL，
            // 它同时开启代码优化（混淆 + 收缩）与资源裁剪，
            // 并默认内置 proguard-android-optimize.txt 的等价规则，
            // 所以不必再手写 isMinifyEnabled / isShrinkResources / proguardFiles。
            // keep 规则放在 app/src/main/keepRules/*.keep，见该目录下的 rules.keep。
            //
            // 体积账：ormbg 模型 168MB 因 noCompress 压不动，
            // 这里能压的是 dex（debug 下 66.5MB）与非目标 ABI 的 native lib。
            optimization {
                enable = true
            }

            // 只保留主流真机 ABI。x86_64 留给 debug，模拟器需要。
            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // 端侧模型 ormbg.tflite（约 176MB）禁止 aapt 压缩，否则 LiteRT 无法 mmap 加载
    // AGP 7+ 用 androidResources 替代已弃用的 aaptOptions
    androidResources {
        noCompress.add("tflite")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Step2 数据层
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.appcompat)

    // 测试
    testImplementation(libs.junit)                       // JVM 单元测试（Step3 健康分引擎）
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    // Step8 导航
    implementation(libs.androidx.navigation.compose)

    // Step16 智能抠图：ormbg-LiteRT（ISNet 架构，TFLite 模型打包进 assets，Google LiteRT 推理，纯端侧无 GMS 依赖）
    implementation(libs.litert)

    // Step23 数据备份：kotlinx.serialization 序列化 JSON（导出/导入）
    implementation(libs.kotlinx.serialization.json)
}
