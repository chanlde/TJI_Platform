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
        buildConfigField(
            "String",
            "TJI_MQTT_BROKER_HOST",
            "\"${configString("TJI_MQTT_BROKER_HOST", "129.211.180.25")}\""
        )
        buildConfigField(
            "int",
            "TJI_MQTT_BROKER_PORT",
            configString("TJI_MQTT_BROKER_PORT", "1883")
        )
        buildConfigField(
            "String",
            "TJI_RADIO_LEGACY_MQTT_HOST",
            "\"${configString("TJI_RADIO_LEGACY_MQTT_HOST", "47.121.127.205")}\""
        )
        buildConfigField(
            "int",
            "TJI_RADIO_LEGACY_MQTT_PORT",
            configString("TJI_RADIO_LEGACY_MQTT_PORT", "1883")
        )
        buildConfigField(
            "String",
            "TJI_RADIO_LEGACY_MQTT_USERNAME",
            "\"${configString("TJI_RADIO_LEGACY_MQTT_USERNAME", "")}\""
        )
        buildConfigField(
            "String",
            "TJI_RADIO_LEGACY_MQTT_PASSWORD",
            "\"${configString("TJI_RADIO_LEGACY_MQTT_PASSWORD", "")}\""
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
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    api(libs.hivemq.mqtt.client)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
