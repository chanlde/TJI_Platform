import com.android.build.gradle.api.ApkVariantOutput
import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import java.util.Properties
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.5.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-service:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.amap.api:3dmap:10.0.600")

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
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

}
