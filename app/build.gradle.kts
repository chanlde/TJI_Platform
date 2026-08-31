import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)

}

detekt {
    buildUponDefaultConfig = false
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/app-baseline.xml")
    source.setFrom(files("src/main/java", "src/test/java"))
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(true)
    }
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
        .orElse(providers.environmentVariable(name))
        .orElse(localProperties.getProperty(name, defaultValue))
        .get()

fun optionalConfigString(name: String): String? =
    providers.gradleProperty(name)
        .orElse(providers.environmentVariable(name))
        .orElse(localProperties.getProperty(name, ""))
        .get()
        .trim()
        .takeIf(String::isNotEmpty)

val releaseStoreFilePath = optionalConfigString("TJI_RELEASE_STORE_FILE")
val releasePasswordFilePath = optionalConfigString("TJI_RELEASE_PASSWORD_FILE")
val releaseKeyAlias = optionalConfigString("TJI_RELEASE_KEY_ALIAS")
val requireReleaseSigning = optionalConfigString("TJI_REQUIRE_RELEASE_SIGNING")
    ?.equals("true", ignoreCase = true) == true
val releaseSigningFields = listOf(
    "TJI_RELEASE_STORE_FILE" to releaseStoreFilePath,
    "TJI_RELEASE_PASSWORD_FILE" to releasePasswordFilePath,
    "TJI_RELEASE_KEY_ALIAS" to releaseKeyAlias
)
val configuredReleaseSigningFields = releaseSigningFields.filter { it.second != null }
if (configuredReleaseSigningFields.isNotEmpty() && configuredReleaseSigningFields.size != releaseSigningFields.size) {
    val missing = releaseSigningFields.filter { it.second == null }.joinToString { it.first }
    throw GradleException("Incomplete release signing configuration. Missing: $missing")
}
val releaseSigningConfigured = configuredReleaseSigningFields.size == releaseSigningFields.size
if (requireReleaseSigning && !releaseSigningConfigured) {
    throw GradleException(
        "Release signing is required. Configure TJI_RELEASE_STORE_FILE, " +
            "TJI_RELEASE_PASSWORD_FILE and TJI_RELEASE_KEY_ALIAS."
    )
}
val releaseSigningPassword: String? = if (releaseSigningConfigured) {
    val passwordFile = file(requireNotNull(releasePasswordFilePath))
    require(passwordFile.isFile) { "Release signing password file does not exist: $passwordFile" }
    passwordFile.readText().trim().also {
        require(it.isNotEmpty()) { "Release signing password file is empty: $passwordFile" }
    }
} else {
    null
}

android {
    namespace = "com.tji.device"
    compileSdk = 35

    flavorDimensions += "map"

    productFlavors {
        create("noMap") {
            dimension = "map"
            buildConfigField("boolean", "ENABLE_AMAP", "false")
        }
        create("map") {
            dimension = "map"
            buildConfigField("boolean", "ENABLE_AMAP", "true")
        }
    }

    defaultConfig {
        applicationId = "com.tji.device"
        minSdk = 24
        targetSdk = 35

        versionCode = APP_VERSION_CODE.toInt()
        versionName = APP_VERSION_NAME

        buildConfigField(
            "String",
            "TJI_RELEASE_SIGNER_SHA256",
            "\"${configString("TJI_RELEASE_SIGNER_SHA256", "0cfc0338819ac62f216737e20634c50bf702fdeb748d9a1a5f3713e22fb76567")}\""
        )
        buildConfigField(
            "int",
            "TJI_APP_UPDATE_PRODUCT_ID",
            configString("TJI_APP_UPDATE_PRODUCT_ID", "-1")
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey
        ndk {
            // 当前设备端 native 音频核心只发布 arm64，避免打入未交付的 ABI。
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
            }
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
            "TJI_SPEAKER_RELAY_TOKEN",
            "\"${configString("TJI_SPEAKER_RELAY_TOKEN", "")}\""
        )
        buildConfigField(
            "String",
            "TJI_SPEAKER_REMOTE_BASE_URL",
            "\"${configString("TJI_SPEAKER_REMOTE_BASE_URL", "http://146.56.250.203:8008")}\""
        )
        buildConfigField(
            "int",
            "TJI_DIRECT_LINK_PORT",
            configString("TJI_DIRECT_LINK_PORT", "19010")
        )
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFilePath))
                storePassword = requireNotNull(releaseSigningPassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = releaseSigningPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "TJI_ENABLE_OTA_TEST_ENTRY", "false")
            buildConfigField("boolean", "TJI_ENABLE_LOCAL_DEMO_DEVICES", "false")
        }
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    androidComponents.onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                val safeVersionName = output.versionName
                    .get()
                    .replace(Regex("[<>:\"/\\\\|?*]"), "_")

                output.outputFileName =
                    "TJI_Platform_${variant.name}_${safeVersionName}_${output.versionCode.get()}.apk"
            }
        }
    }

    packaging {
        jniLibs {
            excludes += setOf(
                "**/libonnxruntime.so",
                "**/libsherpa-onnx-jni.so",
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
    // 仅地图产品包下载和打包高德 SDK；noMap 包使用无线电侦测的示意地图。
    add("mapImplementation", libs.amap3dmap)

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
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

}
