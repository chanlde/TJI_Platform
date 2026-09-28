package com.tji.device.update

import com.tji.device.product.ota.resolveSecureProductOtaDownloadUrl
import com.tji.network.data.AppVersion

internal sealed interface AppUpdateValidation {
    data class Available(
        val url: String,
        val versionCode: Int,
        val apkSha256: String,
        val fileSize: Long,
        val packageName: String,
        val signerSha256: String
    ) : AppUpdateValidation

    data class Unavailable(val reason: String) : AppUpdateValidation
}

internal fun validateAppUpdateCandidate(
    response: AppVersion,
    localVersionCode: Int,
    expectedPackageName: String,
    expectedSignerSha256: String,
    downloadBaseUrl: String
): AppUpdateValidation {
    if (response.type != APP_PACKAGE_TYPE) return unavailable("服务器返回的不是 App 包")
    if (!response.productName.isPlatformProductName()) {
        return unavailable("服务器返回的不是 TJI Platform 产品")
    }
    if (response.packageName != expectedPackageName) {
        return unavailable("App 包名与当前应用不匹配")
    }
    val serverSigner = response.signerSha256.normalizedSha256()
        ?: return unavailable("服务器未返回有效 App 签名摘要")
    if (serverSigner != expectedSignerSha256.normalizedSha256()) {
        return unavailable("App 签名摘要与正式证书不匹配")
    }
    val versionCode = response.innerVersion
        ?.takeIf { it > localVersionCode }
        ?: return unavailable("App 版本不是更高版本")
    val apkSha256 = response.sha256.normalizedSha256()
        ?: return unavailable("服务器未返回有效 App SHA256")
    val fileSize = response.fileSize
        ?.takeIf { it in 1..MAX_APK_BYTES }
        ?: return unavailable("服务器未返回有效 App 文件大小")
    val url = resolveSecureProductOtaDownloadUrl(response.path, downloadBaseUrl)
        ?: return unavailable("App 下载地址必须使用允许域名的 HTTPS")
    if (!url.substringBefore('?').endsWith(".apk", ignoreCase = true)) {
        return unavailable("App 下载地址不是 APK")
    }
    return AppUpdateValidation.Available(
        url = url,
        versionCode = versionCode,
        apkSha256 = apkSha256,
        fileSize = fileSize,
        packageName = expectedPackageName,
        signerSha256 = serverSigner
    )
}

private fun String?.isPlatformProductName(): Boolean {
    val normalized = orEmpty().filter(Char::isLetterOrDigit).lowercase()
    return normalized == "tjiplatform"
}

private fun String?.normalizedSha256(): String? =
    orEmpty().replace(":", "").trim().lowercase().takeIf(SHA256_REGEX::matches)

private fun unavailable(reason: String) = AppUpdateValidation.Unavailable(reason)

private const val APP_PACKAGE_TYPE = 1
private const val MAX_APK_BYTES = 200L * 1024 * 1024
private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")
