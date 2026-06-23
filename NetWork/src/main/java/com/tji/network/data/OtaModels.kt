package com.tji.network.data

import com.google.gson.annotations.SerializedName

data class AppVersion(
    @SerializedName("version")
    val version: String?,
    @SerializedName("innerVersion")
    val innerVersion: Int?,
    @SerializedName("path")
    val path: String? = null,
    @SerializedName("productName")
    val productName: String? = null,
    @SerializedName("techDesc")
    val techDesc: String? = null,
    @SerializedName("type")
    val type: Int? = null
)

data class OtaLatestResponse(
    @SerializedName("id")
    val id: Int? = null,
    @SerializedName("has_update")
    val hasUpdate: Boolean? = null,
    @SerializedName(value = "latest_version", alternate = ["version"])
    val latestVersion: String? = null,
    @SerializedName(value = "hardware_version", alternate = ["hardware"])
    val hardwareVersion: String? = null,
    @SerializedName(value = "file_size", alternate = ["fileSize", "filesize", "size"])
    val fileSize: Long? = null,
    @SerializedName(value = "sha256", alternate = ["sha256Hex"])
    val sha256: String? = null,
    @SerializedName("signature")
    val signature: String? = null,
    @SerializedName("force")
    val force: Boolean? = null,
    @SerializedName("min_battery")
    val minBattery: Int? = null,
    @SerializedName(value = "download_url", alternate = ["downloadUrl", "path"])
    val downloadUrl: String? = null,
    @SerializedName(value = "release_note", alternate = ["releaseNote", "techDesc"])
    val releaseNote: String? = null,
    @SerializedName("productName")
    val productName: String? = null,
    @SerializedName("innerVersion")
    val innerVersion: Int? = null,
    @SerializedName("publishDate")
    val publishDate: String? = null,
    @SerializedName("type")
    val type: Int? = null
)
