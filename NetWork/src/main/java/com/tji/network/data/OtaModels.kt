package com.tji.network.data

import com.google.gson.annotations.SerializedName
import com.google.gson.annotations.JsonAdapter
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

data class AppVersion(
    @SerializedName("version")
    val version: String?,
    @SerializedName("innerVersion")
    @JsonAdapter(FlexibleIntTypeAdapter::class)
    val innerVersion: Int?,
    @SerializedName("path")
    val path: String? = null,
    @SerializedName("productName")
    val productName: String? = null,
    @SerializedName("techDesc")
    val techDesc: String? = null,
    @SerializedName("type")
    val type: Int? = null,
    @SerializedName(value = "packageName", alternate = ["package_name", "applicationId"])
    val packageName: String? = null,
    @SerializedName(value = "signerSha256", alternate = ["signer_sha256", "certificateSha256"])
    val signerSha256: String? = null,
    @SerializedName(value = "sha256", alternate = ["sha256Hex"])
    val sha256: String? = null,
    @SerializedName(value = "fileSize", alternate = ["file_size", "filesize", "size"])
    val fileSize: Long? = null
)

data class OtaLatestResponse(
    @SerializedName("backendFirmwareId")
    val backendFirmwareId: String? = null,
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
    @JsonAdapter(FlexibleIntTypeAdapter::class)
    val innerVersion: Int? = null,
    @SerializedName("publishDate")
    val publishDate: String? = null,
    @SerializedName("type")
    val type: Int? = null
)

class FlexibleIntTypeAdapter : TypeAdapter<Int?>() {
    override fun write(out: JsonWriter, value: Int?) {
        if (value == null) out.nullValue() else out.value(value)
    }

    override fun read(reader: JsonReader): Int? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        return reader.nextString().trim().toIntOrNull()
    }
}
