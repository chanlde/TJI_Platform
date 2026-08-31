package com.tji.device.product.ota

import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.network.data.OtaLatestResponse
import java.net.URI

internal sealed interface ProductOtaPackageValidation {
    data class Valid(val packageInfo: ProductOtaPackage) : ProductOtaPackageValidation
    data class Invalid(val message: String) : ProductOtaPackageValidation
}

internal fun validateProductOtaPackage(
    productType: ProductType,
    deviceInfo: ProductDeviceInfo?,
    latest: OtaLatestResponse,
    otaBaseUrl: String,
    requireSignature: Boolean = false
): ProductOtaPackageValidation {
    if (latest.type != FIRMWARE_PACKAGE_TYPE) {
        return invalid("服务器返回的不是固件包")
    }
    if (!matchesProduct(productType, latest.productName)) {
        return invalid("固件产品与当前设备不匹配")
    }

    val targetVersion = latest.latestVersion?.trim().takeUnless { it.isNullOrEmpty() }
        ?: return invalid("服务器未返回固件版本")
    val currentInnerVersion = deviceInfo?.firmwareInnerVersion
        ?: return invalid("未获取到设备内部版本，禁止升级")
    val targetInnerVersion = latest.innerVersion
        ?: return invalid("服务器未返回固件内部版本")
    if (targetInnerVersion <= currentInnerVersion) {
        return invalid("目标版本不是更高版本，禁止同版本或降级")
    }

    val deviceHardware = deviceInfo.hardwareVersion?.trim().takeUnless { it.isNullOrEmpty() }
    val packageHardware = latest.hardwareVersion?.trim().takeUnless { it.isNullOrEmpty() }
    if (deviceHardware != null && packageHardware != null && deviceHardware != packageHardware) {
        return invalid("固件硬件版本与当前设备不匹配")
    }

    val fileSize = latest.fileSize
        ?.takeIf(::isValidFirmwareSize)
        ?: return invalid("固件文件大小无效")
    val sha256 = latest.sha256?.trim()?.lowercase()
        ?.takeIf(::isValidFirmwareSha256)
        ?: return invalid("固件 SHA256 必须是 64 位十六进制")
    val downloadUrl = resolveSecureProductOtaDownloadUrl(latest.downloadUrl, otaBaseUrl)
        ?: return invalid("固件下载地址必须使用允许域名的 HTTPS")

    val minBattery = latest.minBattery
    if (minBattery != null && minBattery !in 0..100) {
        return invalid("服务器返回的最低电量无效")
    }
    val batteryPercent = deviceInfo.batteryPercent
    if (minBattery != null && batteryPercent != null && batteryPercent < minBattery) {
        return invalid("设备电量低于升级要求")
    }

    val signature = latest.signature?.trim().takeUnless { it.isNullOrEmpty() }
    if (requireSignature && signature == null) {
        return invalid("服务器未返回固件签名")
    }

    return ProductOtaPackageValidation.Valid(
        ProductOtaPackage(
            targetVersion = targetVersion,
            targetInnerVersion = targetInnerVersion,
            hardwareVersion = packageHardware ?: deviceHardware,
            downloadUrl = downloadUrl,
            fileSize = fileSize,
            sha256 = sha256,
            signature = signature
        )
    )
}

internal fun resolveSecureProductOtaDownloadUrl(
    rawUrl: String?,
    otaBaseUrl: String
): String? = runCatching {
    val value = rawUrl?.trim().orEmpty()
    if (value.isEmpty()) return null
    val base = URI(otaBaseUrl).normalize()
    if (!base.isAllowedHttpsEndpoint()) return null
    val resolved = base.resolve(value).normalize()
    if (!resolved.isAllowedHttpsEndpoint()) return null
    if (!resolved.host.equals(base.host, ignoreCase = true)) return null
    if (resolved.rawUserInfo != null || resolved.rawFragment != null) return null
    resolved.toASCIIString()
}.getOrNull()

private fun URI.isAllowedHttpsEndpoint(): Boolean =
    scheme.equals("https", ignoreCase = true) &&
        !host.isNullOrBlank() &&
        (port == -1 || port == HTTPS_PORT)

private fun matchesProduct(productType: ProductType, rawProductName: String?): Boolean {
    val productName = rawProductName?.trim().takeUnless { it.isNullOrEmpty() } ?: return false
    val definition = ProductCatalog.definitionOf(productType)
    return productName.contains(definition.displayName, ignoreCase = true) ||
        productName.contains(definition.productCode, ignoreCase = true)
}

private fun invalid(message: String) = ProductOtaPackageValidation.Invalid(message)

internal fun isValidFirmwareSize(fileSize: Long): Boolean = fileSize in 1..MAX_FIRMWARE_BYTES

internal fun isValidFirmwareSha256(value: String): Boolean = SHA256_REGEX.matches(value)

private const val FIRMWARE_PACKAGE_TYPE = 2
private const val HTTPS_PORT = 443
private const val MAX_FIRMWARE_BYTES = 64L * 1024 * 1024
private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
