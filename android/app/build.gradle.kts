import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 正式发布签名。keystore.properties 与 release.keystore 都在 .gitignore 中，不会进仓库 ——
// 新机器上必须从备份恢复这两样，否则无法给已安装用户发新版（签名不一致会被系统拒绝覆盖）。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "io.github.molishadaze.weijing"
    compileSdk = 35

    signingConfigs {
        create("release") {
            // 仅在配置文件存在时填充。缺失时 release 构建会在签名阶段**明确报错**，
            // 而不是悄悄产出一个装不上的未签名 APK —— 那种失败更晚、更难查。
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "io.github.molishadaze.weijing"
        minSdk = 29
        targetSdk = 35
        // versionCode 必须单调递增，否则已安装旧版的用户会被系统拒绝升级安装。
        // 编码规则：major * 10000 + minor * 100 + patch（1.0 → 1.0.0 → 10000；1.0.1 → 10001）。
        // ⚠️ 不要用「与 versionName 末段对齐」的老写法：那样 1.0.1 和 1.1 都会算成 11，直接撞车。
        // 只改 versionName 不改 versionCode 等于没发新版 —— 两者必须同时改。
        // 另外不少国产启动器按 versionCode 缓存桌面图标，涨号也是图标能刷新的前提。
        versionCode = 10001
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // 用 android/keystore.properties 里的正式 keystore 签名（不再借用 debug 证书）。
            signingConfig = signingConfigs.getByName("release")
            // R8 保持关闭。移除 35.7MB 的 material-icons-extended 之后，未混淆的 release
            // 实测只有 20.90MB（原 58.97MB），暂时不值得引入「崩溃栈变混淆名」的排查成本。
            // 若要再压到 3MB 级：isMinifyEnabled/isShrinkResources 改 true，
            // 并务必归档 app/build/outputs/mapping/release/mapping.txt（按版本号存）。
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room schema 导出目录。exportSchema=true 必须配这个参数，
// 导出的 JSON 要纳入版本管理，供以后编写 Migration 时比对。
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Image loading with Coil
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
}
