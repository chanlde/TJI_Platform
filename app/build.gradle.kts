import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

}
val APP_VERSION_CODE: String  by project
val APP_VERSION_NAME: String by project
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val amapApiKey: String = providers.gradleProperty("AMAP_API_KEY")
    .orElse(localProperties.getProperty("AMAP_API_KEY", ""))
    .get()

fun configString(name: String, defaultValue: String): String =
    providers.gradleProperty(name)
        .orElse(localProperties.getProperty(name, defaultValue))
        .get()

android {
    namespace = "com.tji.device"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tji.device"
        minSdk = 24
        targetSdk = 35

        versionCode = APP_VERSION_CODE.toInt()
        versionName = APP_VERSION_NAME

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey
        ndk {
            // 本地 Kokoro TTS 只面向真实 Android 手机测试；现代小米/华为等设备基本都是 arm64。
            // 这样不会把 x86/armeabi-v7a 的 sherpa/onnxruntime so 一起打进 APK。
            abiFilters += listOf("arm64-v8a")
        }
        buildConfigField(
            "String",
            "TJI_SPEAKER_RELAY_HOST",
            "\"${configString("TJI_SPEAKER_RELAY_HOST", "146.56.250.203")}\""
        )
        buildConfigField(
            "int",
            "TJI_SPEAKER_RELAY_PORT",
            configString("TJI_SPEAKER_RELAY_PORT", "7000")
        )
        buildConfigField(
            "String",
            "TJI_SPEAKER_REMOTE_BASE_URL",
            "\"${configString("TJI_SPEAKER_REMOTE_BASE_URL", "http://146.56.250.203:8008")}\""
        )
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "TJI_ENABLE_OTA_TEST_ENTRY", "true")
            buildConfigField("boolean", "TJI_ENABLE_LOCAL_DEMO_DEVICES", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            buildConfigField("boolean", "TJI_ENABLE_OTA_TEST_ENTRY", "false")
            buildConfigField("boolean", "TJI_ENABLE_LOCAL_DEMO_DEVICES", "false")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true  // 添加这一行

    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    androidComponents.onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                val safeVersionName = output.versionName
                    .get()
                    .replace(Regex("[<>:\"/\\\\|?*]"), "_")

                output.outputFileName = "TJI_Platform_${safeVersionName}.apk"
            }
        }
    }

    // 添加 packaging 配置
    packaging {
        jniLibs {
            // App 通过 libsherpa-onnx-jni.so 调用 sherpa-onnx，不直接使用 C/C++ API。
            excludes += setOf(
                "**/libsherpa-onnx-c-api.so",
                "**/libsherpa-onnx-cxx-api.so"
            )
        }
        resources {
            excludes += setOf(
                "/META-INF/INDEX.LIST",
                "/META-INF/*.kotlin_module",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/NOTICE",
                "/META-INF/io.netty.versions.properties" // 新增
            )
        }
    }
}

dependencies {

    implementation(project(":NetWork"))
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.security.crypto)
    implementation(libs.amap3dmap)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.animation)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

}
