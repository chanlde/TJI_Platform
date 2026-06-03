package com.tji.network.data

data class ApiResponse<T>(
    val code: Int,
    val message: String?,
    val data: T?
)
