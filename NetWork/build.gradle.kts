import org.gradle.kotlin.dsl.implementation
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun configString(name: String, defaultValue: String): String =
    providers.gradleProperty(name)
        .orElse(localProperties.getProperty(name, defaultValue))
        .get()

android {
    namespace = "com.tji.network"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField(
            "String",
            "TJI_API_BASE_URL",
            "\"${configString("TJI_API_BASE_URL", "https://api.tjinnovations.cloud/")}\""
        )
        buildConfigField(
            "String",
            "TJI_OTA_BASE_URL",
            "\"${configString("TJI_OTA_BASE_URL", "https://www.tjinnovations.cloud/")}\""
        )
        buildConfigField(
            "String",
            "TJI_UPDATE_URL",
            "\"${configString("TJI_UPDATE_URL", "http://api.tjinnovations.cloud:81/apks/TJI_Platform.apk")}\""
        )
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
        buildConfig = true
    }
    // 添加 packaging 配置
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/INDEX.LIST",
                "/META-INF/*.kotlin_module",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE",
                "/META-INF/NOTICE"
            )
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")
    api("com.hivemq:hivemq-mqtt-client:1.3.3")
// Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
