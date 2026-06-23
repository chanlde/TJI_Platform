package com.tji.device.data.viewmodel

import com.tji.device.data.repository.AuthRepository
import com.tji.network.data.ApiResponse
import com.tji.network.data.LoginResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LoginViewModelTest {

    @Test
    fun blankAccountIsRejectedBeforeNetworkLogin() {
        val repository = RecordingAuthRepository()
        val viewModel = LoginViewModel(repository)
        var callbackResult: Pair<Boolean, String?>? = null

        viewModel.login("", "password", rememberMe = false) { success, message ->
            callbackResult = success to message
        }

        assertFalse(repository.loginCalled)
        assertEquals(false to "请输入账号", callbackResult)
        assertEquals("请输入账号", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun blankPasswordIsRejectedBeforeNetworkLogin() {
        val repository = RecordingAuthRepository()
        val viewModel = LoginViewModel(repository)
        var callbackResult: Pair<Boolean, String?>? = null

        viewModel.login("account", " ", rememberMe = false) { success, message ->
            callbackResult = success to message
        }

        assertFalse(repository.loginCalled)
        assertEquals(false to "请输入密码", callbackResult)
        assertEquals("请输入密码", viewModel.uiState.value.errorMessage)
    }

    private class RecordingAuthRepository : AuthRepository {
        var loginCalled = false

        override suspend fun login(account: String, password: String): ApiResponse<LoginResponse> {
            loginCalled = true
            error("Login should not be called for invalid local input")
        }

        override suspend fun updateDeviceName(id: Int, productName: String): ApiResponse<Unit> {
            return ApiResponse(code = 200, message = "ok", data = Unit)
        }

        override suspend fun logout() {
            // No-op for this test.
        }
    }
}
