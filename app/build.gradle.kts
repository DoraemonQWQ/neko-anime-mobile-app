import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// 正式版签名：读根目录的 keystore.properties（不在仓库里）。
// 文件不存在也照样能编译，只是产出的 release 包未签名（app-release-unsigned.apk）。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "top.nekoanime.mobile.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "top.nekoanime.mobile.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 9
        versionName = "1.2.1"

        // 用哪个配置文件（真正的值都在 assets/config/config.<模式>.json 里）
        buildConfigField("String", "CONFIG_FILE", "\"config/config.debug.json\"")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            /*
             * dev 版与正式版共存的三件事：
             * 1. applicationId 加 .dev 后缀 → 系统视为两个应用，可以同时安装；
             * 2. 签名用默认的 debug keystore（**不碰 keystore.properties 里的正式签名**）；
             * 3. 名称与图标在 app/src/debug/res 里覆盖（Neko Anime Dev + 带 DEV 角标的图标）。
             */
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }

        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "CONFIG_FILE", "\"config/config.release.json\"")

            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // 播放器：Media3（ExoPlayer）+ 官方 PlayerView（我们用 Compose 自己画控制栏，所以关掉它的默认控制器）
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // 网络图片（番剧封面、用户头像）
    implementation(libs.coil.compose)

    // 接口 JSON
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
