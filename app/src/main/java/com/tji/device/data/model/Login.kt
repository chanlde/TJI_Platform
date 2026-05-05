package com.tji.device.data.model

data class Login(
    val account: String,
    val password: String,
    val rememberMe: Boolean
)

data class LoginUiState(
    val isLoading: Boolean = false,
    var isLoggedIn: Boolean = false,
    val userId: String? = null,
    val errorMessage: String? = null
)
