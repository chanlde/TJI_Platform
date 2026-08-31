package com.tji.device.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.tji.device.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

internal sealed interface AppUpdateInstallResult {
    data object InstallerOpened : AppUpdateInstallResult
    data object PermissionRequested : AppUpdateInstallResult
    data class Failed(val message: String) : AppUpdateInstallResult
}

internal class VerifiedAppUpdateInstaller(
    context: Context
) {
    private val appContext = context.applicationContext

    suspend fun downloadVerifyAndOpen(
        candidate: AppUpdateValidation.Available
    ): AppUpdateInstallResult {
        if (!canRequestPackageInstalls()) {
            openInstallPermissionSettings()
            return AppUpdateInstallResult.PermissionRequested
        }
        val prepared = withContext(Dispatchers.IO) { downloadAndVerify(candidate) }
        return when (prepared) {
            is PreparedApk.Ready -> openSystemInstaller(prepared.file)
            is PreparedApk.Rejected -> AppUpdateInstallResult.Failed(prepared.message)
        }
    }

    private fun downloadAndVerify(candidate: AppUpdateValidation.Available): PreparedApk {
        val updatesDirectory = File(appContext.cacheDir, UPDATE_CACHE_DIRECTORY)
        if (!updatesDirectory.mkdirs() && !updatesDirectory.isDirectory) {
            return PreparedApk.Rejected("无法创建 App 更新缓存目录")
        }
        updatesDirectory.listFiles()?.forEach { stale ->
            if (stale.isFile) stale.delete()
        }
        val partialFile = File(updatesDirectory, "update-${candidate.versionCode}.apk.part")
        val verifiedFile = File(updatesDirectory, "update-${candidate.versionCode}.apk")

        val downloadFailure = runCatching {
            downloadExact(candidate, partialFile)
        }.exceptionOrNull()
        if (downloadFailure != null) {
            partialFile.delete()
            return PreparedApk.Rejected(downloadFailure.message ?: "App 更新下载失败")
        }

        val identity = readDownloadedApkIdentity(appContext, partialFile)
            ?: run {
                partialFile.delete()
                return PreparedApk.Rejected("下载文件不是可解析的 APK")
            }
        return when (val validation = validateDownloadedApkIdentity(candidate, identity)) {
            DownloadedApkValidation.Verified -> {
                if (!partialFile.renameTo(verifiedFile)) {
                    partialFile.delete()
                    PreparedApk.Rejected("无法提交已校验的 App 更新文件")
                } else {
                    PreparedApk.Ready(verifiedFile)
                }
            }
            is DownloadedApkValidation.Rejected -> {
                partialFile.delete()
                PreparedApk.Rejected(validation.reason)
            }
        }
    }

    private fun downloadExact(candidate: AppUpdateValidation.Available, target: File) {
        val connection = URL(candidate.url).openConnection() as? HttpsURLConnection
            ?: error("App 更新地址不是 HTTPS")
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", APK_MIME_TYPE)
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                error("App 更新下载失败，HTTP ${connection.responseCode}")
            }
            val contentLength = connection.contentLengthLong
            if (contentLength >= 0L && contentLength != candidate.fileSize) {
                error("App 更新响应大小与元数据不一致")
            }
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    var totalBytes = 0L
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        totalBytes += count
                        if (totalBytes > candidate.fileSize) {
                            error("App 更新响应超过声明大小")
                        }
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                    if (totalBytes != candidate.fileSize) {
                        error("App 更新下载不完整")
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < 26 || appContext.packageManager.canRequestPackageInstalls()

    private fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${BuildConfig.APPLICATION_ID}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    private fun openSystemInstaller(file: File): AppUpdateInstallResult = runCatching {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        check(intent.resolveActivity(appContext.packageManager) != null) {
            "系统没有可用的 APK 安装器"
        }
        appContext.startActivity(intent)
        AppUpdateInstallResult.InstallerOpened
    }.getOrElse { throwable ->
        file.delete()
        AppUpdateInstallResult.Failed(throwable.message ?: "无法打开系统安装器")
    }

    private sealed interface PreparedApk {
        data class Ready(val file: File) : PreparedApk
        data class Rejected(val message: String) : PreparedApk
    }

    private companion object {
        const val UPDATE_CACHE_DIRECTORY = "verified-app-updates"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
    }
}

internal fun readDownloadedApkIdentity(
    context: Context,
    file: File
): DownloadedApkIdentity? {
    val packageInfo = archivePackageInfo(context.packageManager, file) ?: return null
    val signerDigests = packageInfo.currentSignerBytes()
        .mapTo(linkedSetOf(), ::sha256Hex)
    if (signerDigests.isEmpty()) return null
    return DownloadedApkIdentity(
        fileSize = file.length(),
        apkSha256 = sha256Hex(file),
        packageName = packageInfo.packageName,
        versionCode = packageInfo.archiveVersionCode(),
        signerSha256Digests = signerDigests
    )
}

@Suppress("DEPRECATION")
private fun archivePackageInfo(packageManager: PackageManager, file: File): PackageInfo? =
    if (Build.VERSION.SDK_INT >= 33) {
        packageManager.getPackageArchiveInfo(
            file.absolutePath,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        )
    } else {
        val flags = if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        packageManager.getPackageArchiveInfo(file.absolutePath, flags)
    }

@Suppress("DEPRECATION")
private fun PackageInfo.currentSignerBytes(): List<ByteArray> =
    if (Build.VERSION.SDK_INT >= 28) {
        signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray() }
    } else {
        signatures.orEmpty().map { it.toByteArray() }
    }

@Suppress("DEPRECATION")
private fun PackageInfo.archiveVersionCode(): Long =
    if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

private fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
