package com.tji.device.data.viewmodel

import com.tji.device.MainDispatcherRule
import com.tji.device.data.model.LoginUiState
import com.tji.device.data.model.ProductCatalog
import com.tji.device.data.model.ProductType
import com.tji.device.data.repository.AuthRepository
import com.tji.device.data.session.AppSessionState
import com.tji.device.data.session.AppSessionStore
import com.tji.network.data.ApiResponse
import com.tji.network.data.BoundDeviceRow
import com.tji.network.data.LoginResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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

    @Test
    fun rejectedCredentialsStayLoggedOutAndExposeServerMessage() = runTest {
        val repository = RecordingAuthRepository(
            loginResponse = ApiResponse(code = 401, message = "账号或密码错误", data = null)
        )
        val viewModel = LoginViewModel(repository, accountMqttConnector = { _, _, _ -> })
        var callbackResult: Pair<Boolean, String?>? = null

        viewModel.login("account", "wrong-password", rememberMe = false) { success, message ->
            callbackResult = success to message
        }
        advanceUntilIdle()

        assertTrue(repository.loginCalled)
        assertFalse(viewModel.uiState.value.isLoggedIn)
        assertEquals(false to "账号或密码错误", callbackResult)
        assertEquals("账号或密码错误", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun successfulLoginPublishesOneAccountSessionWithoutChangingDeviceMapping() = runTest {
        val repository = RecordingAuthRepository(
            loginResponse = ApiResponse(
                code = 200,
                message = "ok",
                data = LoginResponse(
                    id = "user-1",
                    token = "token",
                    boundDevices = listOf(
                        BoundDeviceRow(
                            id = 42,
                            serialNumber = "SPEAKER-01",
                            productName = "现场喊话器",
                            productId = 6,
                            productType = "Speaker",
                            productCode = "Speaker"
                        )
                    )
                )
            )
        )
        val sessionStore = AppSessionStore()
        val viewModel = LoginViewModel(
            authRepository = repository,
            sessionStore = sessionStore,
            accountMqttConnector = { _, _, _ -> }
        )
        var callbackResult: Pair<Boolean, String?>? = null

        viewModel.login("account-1", "password", rememberMe = false) { success, message ->
            callbackResult = success to message
        }
        advanceUntilIdle()

        assertEquals(true to null, callbackResult)
        assertTrue(viewModel.uiState.value.isLoggedIn)
        assertEquals("account-1", viewModel.account.value)
        assertEquals("account-1", sessionStore.state.value.account)
        assertEquals("user-1", sessionStore.state.value.userId)
        assertEquals("SPEAKER-01", sessionStore.state.value.boundDevices.single().serialNumber)
        assertEquals("现场喊话器", sessionStore.state.value.boundDevices.single().name)
    }

    @Test
    fun knownProductUsesDeviceNameInsteadOfProductCategoryName() = runTest {
        val repository = RecordingAuthRepository(
            loginResponse = ApiResponse(
                code = 200,
                message = "ok",
                data = LoginResponse(
                    id = "user-device-name",
                    token = "token",
                    boundDevices = listOf(
                        BoundDeviceRow(
                            id = 66,
                            serialNumber = "SPEAKER-RENAMED-01",
                            name = "旧设备名",
                            deviceName = "现场喊话器 01",
                            productName = "喊话器",
                            productId = 6,
                            productType = "Speaker",
                            productCode = "Speaker"
                        )
                    )
                )
            )
        )
        val sessionStore = AppSessionStore()
        val viewModel = LoginViewModel(
            authRepository = repository,
            sessionStore = sessionStore,
            accountMqttConnector = { _, _, _ -> }
        )

        viewModel.login("account", "password", rememberMe = false) { _, _ -> }
        advanceUntilIdle()

        assertEquals("现场喊话器 01", sessionStore.state.value.boundDevices.single().name)
    }

    @Test
    fun successfulLoginPreservesCatalogProductMappingAndBackendDeviceOrder() = runTest {
        val rows = ProductCatalog.enabledDefinitions.mapIndexed { index, definition ->
            BoundDeviceRow(
                id = index + 1,
                serialNumber = "DEVICE-${index + 1}",
                productName = "设备-${index + 1}",
                productId = definition.productId,
                productType = definition.productCode,
                productCode = definition.productCode
            )
        }
        val repository = RecordingAuthRepository(
            loginResponse = ApiResponse(
                code = 200,
                message = "ok",
                data = LoginResponse(id = "user-all", token = "token", boundDevices = rows)
            )
        )
        val sessionStore = AppSessionStore()
        val viewModel = LoginViewModel(
            authRepository = repository,
            sessionStore = sessionStore,
            accountMqttConnector = { _, _, _ -> }
        )

        viewModel.login("all-products", "password", rememberMe = false) { _, _ -> }
        advanceUntilIdle()

        assertEquals(
            ProductCatalog.enabledDefinitions.map { it.type },
            sessionStore.state.value.boundDevices.map { it.productType }
        )
        assertEquals(rows.map { it.serialNumber }, sessionStore.state.value.boundDevices.map { it.serialNumber })
        assertEquals(rows.map { it.productName }, sessionStore.state.value.boundDevices.map { it.name })
    }

    @Test
    fun adminCreatedProductStaysReadOnlyAndNeverBecomesFireBucket() = runTest {
        val repository = RecordingAuthRepository(
            loginResponse = ApiResponse(
                code = 200,
                message = "ok",
                data = LoginResponse(
                    id = "user-catalog",
                    token = "token",
                    boundDevicesComplete = true,
                    boundDevices = listOf(
                        BoundDeviceRow(
                            serialNumber = "SPEAKER-001",
                            deviceName = "现场喊话器",
                            productName = "喊话器",
                            productCode = "Speaker"
                        ),
                        BoundDeviceRow(
                            serialNumber = "TANK-001",
                            deviceName = "水箱样机",
                            productName = "消防水箱",
                            productType = "FireWaterTank",
                            productCode = "FireWaterTank"
                        )
                    )
                )
            )
        )
        val sessionStore = AppSessionStore()
        val viewModel = LoginViewModel(
            authRepository = repository,
            sessionStore = sessionStore,
            accountMqttConnector = { _, _, _ -> }
        )

        viewModel.login("account", "password", rememberMe = false) { _, _ -> }
        advanceUntilIdle()

        assertEquals(listOf("SPEAKER-001"), sessionStore.state.value.boundDevices.map { it.serialNumber })
        assertEquals("TANK-001", sessionStore.state.value.catalogDevices.single().serialNumber)
        assertEquals("FireWaterTank", sessionStore.state.value.catalogDevices.single().productCode)
        assertEquals("消防水箱", sessionStore.state.value.catalogDevices.single().productName)
        assertEquals(null, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun bucketProductRowWithHydroGunLinkNameIsPublishedAsFireGun() = runTest {
        val repository = RecordingAuthRepository(
            loginResponse = ApiResponse(
                code = 200,
                message = "ok",
                data = LoginResponse(
                    id = "user-fire-gun",
                    token = "token",
                    boundDevices = listOf(
                        BoundDeviceRow(
                            id = 1,
                            serialNumber = "E465B062174A5124",
                            sn = "7003DEF5",
                            productName = "HydroGunLink_V1-9526D839",
                            productId = 2,
                            productType = "FireBucket",
                            productCode = "FireBucket"
                        )
                    )
                )
            )
        )
        val sessionStore = AppSessionStore()
        val viewModel = LoginViewModel(
            authRepository = repository,
            sessionStore = sessionStore,
            accountMqttConnector = { _, _, _ -> }
        )

        viewModel.login("fire-gun-account", "password", rememberMe = false) { _, _ -> }
        advanceUntilIdle()

        val device = sessionStore.state.value.boundDevices.single()
        assertEquals(ProductType.FireGun, device.productType)
        assertEquals("E465B062174A5124", device.serialNumber)
        assertEquals("HydroGunLink_V1-9526D839", device.name)
    }

    @Test
    fun successfulLoginFiltersDisabledProductRowsBeforePublishingSession() = runTest {
        val repository = RecordingAuthRepository(
            loginResponse = ApiResponse(
                code = 200,
                message = "ok",
                data = LoginResponse(
                    id = "user-1",
                    token = "token",
                    boundDevices = listOf(
                        BoundDeviceRow(
                            id = 8,
                            serialNumber = "SEARCHLIGHT-01",
                            productName = "未交付探照灯",
                            productId = 8,
                            productType = "Searchlight",
                            productCode = "Searchlight"
                        ),
                        BoundDeviceRow(
                            id = 6,
                            serialNumber = "SPEAKER-01",
                            productName = "喊话器",
                            productId = 6,
                            productType = "Speaker",
                            productCode = "Speaker"
                        )
                    )
                )
            )
        )
        val sessionStore = AppSessionStore()
        val viewModel = LoginViewModel(
            authRepository = repository,
            sessionStore = sessionStore,
            accountMqttConnector = { _, _, _ -> }
        )

        viewModel.login("account", "password", rememberMe = false) { _, _ -> }
        advanceUntilIdle()

        assertEquals(listOf("SPEAKER-01"), sessionStore.state.value.boundDevices.map { it.serialNumber })
        assertTrue(sessionStore.state.value.boundDevices.all { ProductCatalog.isEnabled(it.productType) })
    }

    @Test
    fun logoutClearsAuthAndSessionBeforeRootCanShowLoginAgain() = runTest {
        val repository = RecordingAuthRepository(
            loginResponse = ApiResponse(
                code = 200,
                message = "ok",
                data = LoginResponse(id = "user-1", token = "token")
            )
        )
        val sessionStore = AppSessionStore()
        var mqttDisconnected = false
        val viewModel = LoginViewModel(
            authRepository = repository,
            sessionStore = sessionStore,
            mqttDisconnectAll = { mqttDisconnected = true },
            accountMqttConnector = { _, _, _ -> }
        )
        viewModel.login("account-1", "password", rememberMe = false) { _, _ -> }
        advanceUntilIdle()

        var logoutCompleted = false
        viewModel.logout { logoutCompleted = true }
        assertTrue(viewModel.uiState.value.isLoggingOut)
        advanceUntilIdle()

        assertTrue(repository.logoutCalled)
        assertTrue(mqttDisconnected)
        assertTrue(logoutCompleted)
        assertEquals(LoginUiState(), viewModel.uiState.value)
        assertEquals("", viewModel.account.value)
        assertEquals(AppSessionState(), sessionStore.state.value)
    }

    @Test
    fun logoutThenLoginWithAnotherAccountReplacesTheWholeSession() = runTest {
        val repository = RecordingAuthRepository()
        val sessionStore = AppSessionStore()
        val viewModel = LoginViewModel(
            authRepository = repository,
            sessionStore = sessionStore,
            mqttDisconnectAll = {},
            accountMqttConnector = { _, _, _ -> }
        )

        repository.loginResponse = successfulLogin(
            userId = "user-a",
            serialNumber = "SPEAKER-A",
            productName = "A 的喊话器"
        )
        viewModel.login("account-a", "password-a", rememberMe = false) { _, _ -> }
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        repository.loginResponse = successfulLogin(
            userId = "user-b",
            serialNumber = "SPEAKER-B",
            productName = "B 的喊话器"
        )
        viewModel.login("account-b", "password-b", rememberMe = false) { _, _ -> }
        advanceUntilIdle()

        assertEquals("account-b", sessionStore.state.value.account)
        assertEquals("user-b", sessionStore.state.value.userId)
        assertEquals(listOf("SPEAKER-B"), sessionStore.state.value.boundDevices.map { it.serialNumber })
        assertFalse(sessionStore.state.value.boundDevices.any { it.serialNumber == "SPEAKER-A" })
    }

    private fun successfulLogin(
        userId: String,
        serialNumber: String,
        productName: String
    ): ApiResponse<LoginResponse> = ApiResponse(
        code = 200,
        message = "ok",
        data = LoginResponse(
            id = userId,
            token = "token-$userId",
            boundDevices = listOf(
                BoundDeviceRow(
                    id = 42,
                    serialNumber = serialNumber,
                    productName = productName,
                    productId = 6,
                    productType = "Speaker",
                    productCode = "Speaker"
                )
            )
        )
    )

    private class RecordingAuthRepository(
        var loginResponse: ApiResponse<LoginResponse>? = null
    ) : AuthRepository {
        var loginCalled = false
        var logoutCalled = false

        override suspend fun login(account: String, password: String): ApiResponse<LoginResponse> {
            loginCalled = true
            return loginResponse ?: error("Login should not be called for invalid local input")
        }

        override suspend fun updateDeviceName(id: Int, productName: String): ApiResponse<Unit> {
            return ApiResponse(code = 200, message = "ok", data = Unit)
        }

        override suspend fun logout() {
            logoutCalled = true
        }
    }
}
