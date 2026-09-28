package com.tji.network.data

data class OtaTaskRequest(
    val deviceSn: String,
    val firmwarePackageId: String,
    val clientRequestId: String
)

data class OtaTaskResponse(
    val id: String,
    val cmdId: String,
    val deviceSn: String,
    val firmwarePackageId: String,
    val status: String,
    val progress: Int,
    val lastMessage: String? = null,
    val hardwareVersion: String,
    val fromInnerVersion: Int,
    val targetVersion: String,
    val targetInnerVersion: Int,
    val targetSha256: String,
    val fileSize: Long,
    val downloadUrl: String,
    val lastEventSeq: Int,
    val createdAt: String,
    val finishedAt: String? = null
)
