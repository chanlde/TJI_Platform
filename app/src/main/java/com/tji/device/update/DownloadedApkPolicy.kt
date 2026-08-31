package com.tji.device.update

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

internal data class DownloadedApkIdentity(
    val fileSize: Long,
    val apkSha256: String,
    val packageName: String,
    val versionCode: Long,
    val signerSha256Digests: Set<String>
)

internal sealed interface DownloadedApkValidation {
    data object Verified : DownloadedApkValidation
    data class Rejected(val reason: String) : DownloadedApkValidation
}

internal fun validateDownloadedApkIdentity(
    candidate: AppUpdateValidation.Available,
    actual: DownloadedApkIdentity
): DownloadedApkValidation {
    if (actual.fileSize != candidate.fileSize) {
        return rejected("下载 APK 大小与服务器元数据不一致")
    }
    if (!actual.apkSha256.equals(candidate.apkSha256, ignoreCase = true)) {
        return rejected("下载 APK SHA256 校验失败")
    }
    if (actual.packageName != candidate.packageName) {
        return rejected("下载 APK 包名与当前应用不匹配")
    }
    if (actual.versionCode != candidate.versionCode.toLong()) {
        return rejected("下载 APK 版本与服务器元数据不一致")
    }
    if (actual.signerSha256Digests.none { it.equals(candidate.signerSha256, ignoreCase = true) }) {
        return rejected("下载 APK 实际签名证书不匹配")
    }
    return DownloadedApkValidation.Verified
}

internal fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
        val buffer = ByteArray(DIGEST_BUFFER_BYTES)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count > 0) digest.update(buffer, 0, count)
        }
    }
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun rejected(reason: String) = DownloadedApkValidation.Rejected(reason)

private const val DIGEST_BUFFER_BYTES = 64 * 1024
