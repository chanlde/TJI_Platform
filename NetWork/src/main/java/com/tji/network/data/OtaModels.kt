package com.tji.network.data

import com.google.gson.annotations.SerializedName

data class AppVersion(
    val version: String?,
    val innerVersion: Int?,
    val path: String? = null,
    val productName: String? = null,
    val techDesc: String? = null,
    val type: Int? = null
)

data class OtaLatestResponse(
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
    val signature: String? = null,
    val force: Boolean? = null,
    @SerializedName("min_battery")
    val minBattery: Int? = null,
    @SerializedName(value = "download_url", alternate = ["downloadUrl", "path"])
    val downloadUrl: String? = null,
    @SerializedName(value = "release_note", alternate = ["releaseNote", "techDesc"])
    val releaseNote: String? = null,
    val productName: String? = null,
    val innerVersion: Int? = null,
    val publishDate: String? = null,
    val type: Int? = null
)
