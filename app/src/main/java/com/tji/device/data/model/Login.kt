package com.tji.device.data.model

data class Login(
    val account: String,
    val password: String,
    val rememberMe: Boolean
)

sealed interface AuthState {
    data object LoggedOut : AuthState
    data class LoggedIn(val account: String) : AuthState
    data class LoggingOut(val account: String) : AuthState
}

data class LoginUiState(
    val isLoading: Boolean = false,
    val authState: AuthState = AuthState.LoggedOut,
    val userId: String? = null,
    val errorMessage: String? = null
) {
    val isLoggedIn: Boolean
        get() = authState is AuthState.LoggedIn || authState is AuthState.LoggingOut

    val isLoggingOut: Boolean
        get() = authState is AuthState.LoggingOut
}
