package com.tji.device.product.ota

import com.tji.device.MainDispatcherRule
import com.tji.device.data.model.ProductType
import com.tji.network.data.OtaLatestResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProductOtaViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun validServerPackagePublishesConfirmedMcuFields() = runTest {
        val publisher = RecordingPublisher()
        val viewModel = ProductOtaViewModel(FixedRepository(latest()), publisher)
        viewModel.resetForDevice(SERIAL, ProductType.Speaker)

        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()
        assertTrue(viewModel.otaCheckState.value.hasUpdate)

        viewModel.startOta(SERIAL, ProductType.Speaker, deviceInfo())

        assertNotNull(publisher.packageInfo)
        val published = requireNotNull(publisher.packageInfo)
        assertEquals("V1.0.2", published.targetVersion)
        assertEquals(12, published.targetInnerVersion)
        assertEquals(SHA256, published.sha256)
        assertEquals("https://www.tjinnovations.cloud/download/speaker.bin", published.downloadUrl)
        assertEquals(ProductOtaCommandFeedbackStatus.Success, viewModel.commandFeedback.value.status)
    }

    @Test
    fun unsafePackageNeverReachesPublisher() = runTest {
        val publisher = RecordingPublisher()
        val unsafe = latest(downloadUrl = "https://evil.example/speaker.bin")
        val viewModel = ProductOtaViewModel(FixedRepository(unsafe), publisher)
        viewModel.resetForDevice(SERIAL, ProductType.Speaker)
        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()

        viewModel.startOta(SERIAL, ProductType.Speaker, deviceInfo())

        assertNull(publisher.packageInfo)
        assertTrue(viewModel.otaCheckState.value.errorMessage.orEmpty().contains("HTTPS"))
    }

    @Test
    fun serverHasUpdateCannotForceSameVersionOrDowngrade() = runTest {
        val publisher = RecordingPublisher()
        val viewModel = ProductOtaViewModel(
            FixedRepository(latest(innerVersion = 11)),
            publisher
        )
        viewModel.resetForDevice(SERIAL, ProductType.Speaker)

        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()

        assertTrue(!viewModel.otaCheckState.value.hasUpdate)
        viewModel.startOta(SERIAL, ProductType.Speaker, deviceInfo())
        assertNull(publisher.packageInfo)
    }

    @Test
    fun newSessionClearsOldReservationForSameDevice() = runTest {
        val publisher = RecordingPublisher()
        val viewModel = ProductOtaViewModel(FixedRepository(latest()), publisher)
        viewModel.resetForDevice(SERIAL, ProductType.Speaker, sessionGeneration = 1)
        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()
        viewModel.startOta(SERIAL, ProductType.Speaker, deviceInfo())
        assertEquals(1, publisher.startCount)

        viewModel.resetForDevice(SERIAL, ProductType.Speaker, sessionGeneration = 2)
        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()
        viewModel.startOta(SERIAL, ProductType.Speaker, deviceInfo())

        assertEquals(2, publisher.startCount)
    }

    @Test
    fun deviceSwitchRejectsLateFirmwareCheckResult() = runTest {
        val repository = DeferredRepository()
        val viewModel = ProductOtaViewModel(repository, RecordingPublisher())
        viewModel.resetForDevice(SERIAL, ProductType.Speaker, sessionGeneration = 1)

        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()
        viewModel.resetForDevice("SPEAKER-02", ProductType.Speaker, sessionGeneration = 1)
        repository.result.complete(Result.success(latest()))
        advanceUntilIdle()

        assertEquals(ProductOtaCheckState(), viewModel.otaCheckState.value)
    }

    @Test
    fun deviceSwitchRejectsLateOtaPublishCallback() = runTest {
        val publisher = DeferredPublisher()
        val viewModel = ProductOtaViewModel(FixedRepository(latest()), publisher)
        viewModel.resetForDevice(SERIAL, ProductType.Speaker, sessionGeneration = 1)
        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()
        viewModel.startOta(SERIAL, ProductType.Speaker, deviceInfo())

        viewModel.resetForDevice("SPEAKER-02", ProductType.Speaker, sessionGeneration = 1)
        requireNotNull(publisher.onSuccess).invoke()

        assertEquals(ProductOtaCommandFeedback(), viewModel.commandFeedback.value)
    }

    @Test
    fun unbindCancelsCheckAndClearsDeviceScopedState() = runTest {
        val repository = DeferredRepository()
        val viewModel = ProductOtaViewModel(repository, RecordingPublisher())
        viewModel.resetForDevice(SERIAL, ProductType.Speaker, sessionGeneration = 1)
        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()
        assertTrue(viewModel.otaCheckState.value.isChecking)

        viewModel.unbindDevice(SERIAL, ProductType.Speaker)
        repository.result.complete(Result.success(latest()))
        advanceUntilIdle()

        assertEquals(ProductOtaCheckState(), viewModel.otaCheckState.value)
        assertEquals(ProductOtaCommandFeedback(), viewModel.commandFeedback.value)
    }

    @Test
    fun unbindDoesNotPermitDuplicateOtaForSameSessionAndDevice() = runTest {
        val publisher = RecordingPublisher()
        val viewModel = ProductOtaViewModel(FixedRepository(latest()), publisher)
        viewModel.resetForDevice(SERIAL, ProductType.Speaker, sessionGeneration = 1)
        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()
        viewModel.startOta(SERIAL, ProductType.Speaker, deviceInfo())
        assertEquals(1, publisher.startCount)

        viewModel.unbindDevice(SERIAL, ProductType.Speaker)
        viewModel.resetForDevice(SERIAL, ProductType.Speaker, sessionGeneration = 1)
        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()
        viewModel.startOta(SERIAL, ProductType.Speaker, deviceInfo())

        assertEquals(1, publisher.startCount)
    }

    private class FixedRepository(
        private val response: OtaLatestResponse
    ) : ProductOtaRepository {
        override suspend fun getLatestFirmware(productId: Int): Result<OtaLatestResponse> =
            Result.success(response)
    }

    private class DeferredRepository : ProductOtaRepository {
        val result = CompletableDeferred<Result<OtaLatestResponse>>()

        override suspend fun getLatestFirmware(productId: Int): Result<OtaLatestResponse> =
            result.await()
    }

    private class DeferredPublisher : ProductOtaCommandPublisher {
        var onSuccess: (() -> Unit)? = null

        override fun requestDeviceInfo(
            serialNumber: String,
            productType: ProductType,
            msgId: String,
            onSuccess: () -> Unit,
            onError: (Throwable) -> Unit
        ) = Unit

        override fun startOta(
            serialNumber: String,
            productType: ProductType,
            msgId: String,
            packageInfo: ProductOtaPackage,
            onSuccess: () -> Unit,
            onError: (Throwable) -> Unit
        ) {
            this.onSuccess = onSuccess
        }
    }

    private class RecordingPublisher : ProductOtaCommandPublisher {
        var packageInfo: ProductOtaPackage? = null
        var startCount: Int = 0

        override fun requestDeviceInfo(
            serialNumber: String,
            productType: ProductType,
            msgId: String,
            onSuccess: () -> Unit,
            onError: (Throwable) -> Unit
        ) = onSuccess()

        override fun startOta(
            serialNumber: String,
            productType: ProductType,
            msgId: String,
            packageInfo: ProductOtaPackage,
            onSuccess: () -> Unit,
            onError: (Throwable) -> Unit
        ) {
            startCount += 1
            this.packageInfo = packageInfo
            onSuccess()
        }
    }

    private fun deviceInfo() = ProductDeviceInfo(
        hardwareVersion = "HW-A",
        firmwareVersion = "V1.0.1",
        firmwareInnerVersion = 11,
        batteryPercent = 80
    )

    private fun latest(
        innerVersion: Int = 12,
        downloadUrl: String = "/download/speaker.bin"
    ) = OtaLatestResponse(
        hasUpdate = true,
        latestVersion = "V1.0.2",
        hardwareVersion = "HW-A",
        fileSize = 216_256,
        sha256 = SHA256,
        downloadUrl = downloadUrl,
        productName = "无人机喊话器",
        innerVersion = innerVersion,
        type = 2
    )

    private companion object {
        const val SERIAL = "SPEAKER-01"
        const val SHA256 = "f51563a1db560764eda95a6f2f0c4fddfbcf7870efc6bcd5457025ee157b3509"
    }
}
