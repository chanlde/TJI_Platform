package com.tji.device.product.ota

import com.tji.device.MainDispatcherRule
import com.tji.device.data.model.ProductType
import com.tji.device.data.session.DeviceKey
import com.tji.network.data.OtaLatestResponse
import com.tji.network.data.OtaTaskRequest
import com.tji.network.data.OtaTaskResponse
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val viewModel = createViewModel(FixedRepository(latest()), publisher)
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
        assertTrue(viewModel.isOtaStartReserved.value)
    }

    @Test
    fun firmwareCheckUsesDeviceHardwareVersionAndOmitsUnknownVersion() = runTest {
        val repository = RecordingRepository(latest())
        val viewModel = createViewModel(repository, RecordingPublisher())
        viewModel.resetForDevice(SERIAL, ProductType.Speaker)

        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo().copy(hardwareVersion = " HW-A "))
        advanceUntilIdle()
        assertEquals("HW-A", repository.hardwareVersions.last())

        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo().copy(hardwareVersion = "  "))
        advanceUntilIdle()
        assertNull(repository.hardwareVersions.last())
    }

    @Test
    fun unsafePackageNeverReachesPublisher() = runTest {
        val publisher = RecordingPublisher()
        val unsafe = latest(downloadUrl = "https://evil.example/speaker.bin")
        val viewModel = createViewModel(FixedRepository(unsafe), publisher)
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
        val viewModel = createViewModel(
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
    fun reenteringDeviceKeepsReservationWithoutTerminalEvidence() = runTest {
        val publisher = RecordingPublisher()
        val viewModel = createViewModel(FixedRepository(latest()), publisher)
        viewModel.resetForDevice(SERIAL, ProductType.Speaker)
        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()
        viewModel.startOta(SERIAL, ProductType.Speaker, deviceInfo())
        assertEquals(1, publisher.startCount)
        assertTrue(viewModel.isOtaStartReserved.value)

        viewModel.resetForDevice(SERIAL, ProductType.Speaker)
        assertTrue(viewModel.isOtaStartReserved.value)
        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()
        viewModel.startOta(SERIAL, ProductType.Speaker, deviceInfo())

        assertEquals(1, publisher.startCount)
    }

    @Test
    fun taskCapableDeviceUsesReservedCommandAndWaitsForServerTerminalProof() = runTest {
        val repository = TaskRepository(latest(backendFirmwareId = "firmware-1"))
        val publisher = RecordingPublisher()
        val viewModel = createViewModel(repository, publisher)
        val capableInfo = deviceInfo().copy(otaTaskProtocolVersion = 1)
        viewModel.resetForDevice(SERIAL, ProductType.Speaker)
        viewModel.checkOta(SERIAL, ProductType.Speaker, capableInfo)
        advanceUntilIdle()

        viewModel.startOta(SERIAL, ProductType.Speaker, capableInfo)
        advanceUntilIdle()

        assertEquals(1, repository.reservationCount)
        assertEquals(1, publisher.startCount)
        assertEquals("server-cmd-1", publisher.lastMsgId)
        assertEquals("server-task-1", publisher.packageInfo?.taskId)
        assertTrue(viewModel.isOtaStartReserved.value)
        assertEquals("RESERVED", viewModel.serverTask.value?.status)

        viewModel.onOtaStatusChanged(SERIAL, ProductType.Speaker, ProductOtaStatus("SUCCESS", cmdId = "server-cmd-1"))
        advanceUntilIdle()
        assertTrue(viewModel.isOtaStartReserved.value)
        assertEquals("RESERVED", viewModel.serverTask.value?.status)

        repository.task = repository.task.copy(status = "SUCCESS", progress = 100, finishedAt = "2026-09-27T00:00:00Z")
        viewModel.onOtaStatusChanged(SERIAL, ProductType.Speaker, ProductOtaStatus("SUCCESS", cmdId = "server-cmd-1"))
        advanceUntilIdle()
        assertFalse(viewModel.isOtaStartReserved.value)
        assertEquals("SUCCESS", viewModel.serverTask.value?.status)
    }

    @Test
    fun newViewModelRestoresOtherAccountActiveTaskBeforeAllowingPublish() = runTest {
        val repository = TaskRepository(latest(backendFirmwareId = "firmware-1"))
        repository.activeTask = repository.task
        val publisher = RecordingPublisher()
        val viewModel = createViewModel(repository, publisher)
        val capableInfo = deviceInfo().copy(otaTaskProtocolVersion = 1)
        viewModel.resetForDevice(SERIAL, ProductType.Speaker)
        viewModel.restoreActiveTask(SERIAL, ProductType.Speaker, capableInfo)
        advanceUntilIdle()
        assertTrue(viewModel.isOtaStartReserved.value)
        assertEquals("server-task-1", viewModel.serverTask.value?.id)

        viewModel.checkOta(SERIAL, ProductType.Speaker, capableInfo)
        advanceUntilIdle()
        viewModel.startOta(SERIAL, ProductType.Speaker, capableInfo)
        advanceUntilIdle()
        assertEquals(0, repository.reservationCount)
        assertEquals(0, publisher.startCount)
    }

    @Test
    fun processRecreationKeepsUnknownTaskLockedWhileDeviceIsOffline() = runTest {
        val taskStore = InMemoryProductOtaTaskStore()
        val repository = TaskRepository(latest(backendFirmwareId = "firmware-1"))
        val capableInfo = deviceInfo().copy(otaTaskProtocolVersion = 1)
        val originalPublisher = RecordingPublisher()
        val original = createViewModel(repository, originalPublisher, taskStore)
        original.resetForDevice(SERIAL, ProductType.Speaker)
        original.checkOta(SERIAL, ProductType.Speaker, capableInfo)
        advanceUntilIdle()
        original.startOta(SERIAL, ProductType.Speaker, capableInfo)
        advanceUntilIdle()
        assertEquals(1, originalPublisher.startCount)
        assertEquals("server-task-1", taskStore.get(DeviceKey(ProductType.Speaker, SERIAL))?.taskId)

        repository.task = repository.task.copy(status = "UNKNOWN", lastEventSeq = 2)
        repository.activeTask = repository.task
        val recreatedPublisher = RecordingPublisher()
        val recreated = createViewModel(repository, recreatedPublisher, taskStore)
        recreated.resetForDevice(SERIAL, ProductType.Speaker)
        assertTrue(recreated.isOtaStartReserved.value)
        recreated.restoreActiveTask(SERIAL, ProductType.Speaker, deviceInfo = null)
        advanceUntilIdle()
        assertEquals("UNKNOWN", recreated.serverTask.value?.status)
        assertTrue(recreated.isOtaStartReserved.value)

        recreated.checkOta(SERIAL, ProductType.Speaker, capableInfo)
        advanceUntilIdle()
        recreated.startOta(SERIAL, ProductType.Speaker, capableInfo)
        advanceUntilIdle()
        assertEquals(1, repository.reservationCount)
        assertEquals(0, recreatedPublisher.startCount)

        repository.task = repository.task.copy(
            status = "FAILED",
            finishedAt = "2026-09-27T01:00:00Z"
        )
        repository.activeTask = null
        recreated.restoreActiveTask(SERIAL, ProductType.Speaker, deviceInfo = null)
        advanceUntilIdle()
        assertEquals("FAILED", recreated.serverTask.value?.status)
        assertFalse(recreated.isOtaStartReserved.value)
        assertNull(taskStore.get(DeviceKey(ProductType.Speaker, SERIAL)))
    }

    @Test
    fun deviceSwitchRejectsLateFirmwareCheckResult() = runTest {
        val repository = DeferredRepository()
        val viewModel = createViewModel(repository, RecordingPublisher())
        viewModel.resetForDevice(SERIAL, ProductType.Speaker)

        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()
        viewModel.resetForDevice("SPEAKER-02", ProductType.Speaker)
        repository.result.complete(Result.success(latest()))
        advanceUntilIdle()

        assertEquals(ProductOtaCheckState(), viewModel.otaCheckState.value)
    }

    @Test
    fun deviceSwitchRejectsLateOtaPublishCallback() = runTest {
        val publisher = DeferredPublisher()
        val viewModel = createViewModel(FixedRepository(latest()), publisher)
        viewModel.resetForDevice(SERIAL, ProductType.Speaker)
        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()
        viewModel.startOta(SERIAL, ProductType.Speaker, deviceInfo())

        viewModel.resetForDevice("SPEAKER-02", ProductType.Speaker)
        assertFalse(viewModel.isOtaStartReserved.value)
        requireNotNull(publisher.onSuccess).invoke()

        assertEquals(ProductOtaCommandFeedback(), viewModel.commandFeedback.value)
    }

    @Test
    fun unbindCancelsCheckAndClearsDeviceScopedState() = runTest {
        val repository = DeferredRepository()
        val viewModel = createViewModel(repository, RecordingPublisher())
        viewModel.resetForDevice(SERIAL, ProductType.Speaker)
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
        val viewModel = createViewModel(FixedRepository(latest()), publisher)
        viewModel.resetForDevice(SERIAL, ProductType.Speaker)
        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()
        viewModel.startOta(SERIAL, ProductType.Speaker, deviceInfo())
        assertEquals(1, publisher.startCount)
        assertTrue(viewModel.isOtaStartReserved.value)

        viewModel.unbindDevice(SERIAL, ProductType.Speaker)
        assertFalse(viewModel.isOtaStartReserved.value)
        viewModel.resetForDevice(SERIAL, ProductType.Speaker)
        assertTrue(viewModel.isOtaStartReserved.value)
        viewModel.checkOta(SERIAL, ProductType.Speaker, deviceInfo())
        advanceUntilIdle()
        viewModel.startOta(SERIAL, ProductType.Speaker, deviceInfo())

        assertEquals(1, publisher.startCount)
    }

    private fun createViewModel(
        repository: ProductOtaRepository,
        publisher: ProductOtaCommandPublisher,
        taskStore: ProductOtaTaskStore = InMemoryProductOtaTaskStore()
    ) = ProductOtaViewModel(
        repository = repository,
        commandPublisher = publisher,
        otaBaseUrl = "https://www.tjinnovations.cloud/",
        taskStore = taskStore
    )

    private class FixedRepository(
        private val response: OtaLatestResponse
    ) : ProductOtaRepository {
        override suspend fun getLatestFirmware(productId: Int, hardwareVersion: String?): Result<OtaLatestResponse> =
            Result.success(response)
    }

    private class RecordingRepository(
        private val response: OtaLatestResponse
    ) : ProductOtaRepository {
        val hardwareVersions = mutableListOf<String?>()

        override suspend fun getLatestFirmware(productId: Int, hardwareVersion: String?): Result<OtaLatestResponse> {
            hardwareVersions += hardwareVersion
            return Result.success(response)
        }
    }

    private class DeferredRepository : ProductOtaRepository {
        val result = CompletableDeferred<Result<OtaLatestResponse>>()

        override suspend fun getLatestFirmware(productId: Int, hardwareVersion: String?): Result<OtaLatestResponse> =
            result.await()
    }

    private class TaskRepository(
        private val latest: OtaLatestResponse
    ) : ProductOtaRepository {
        var reservationCount = 0
        var activeTask: OtaTaskResponse? = null
        var task = OtaTaskResponse(
            id = "server-task-1",
            cmdId = "server-cmd-1",
            deviceSn = SERIAL,
            firmwarePackageId = "firmware-1",
            status = "RESERVED",
            progress = 0,
            hardwareVersion = "HW-A",
            fromInnerVersion = 11,
            targetVersion = "V1.0.2",
            targetInnerVersion = 12,
            targetSha256 = SHA256,
            fileSize = 216_256,
            downloadUrl = "/download/speaker.bin",
            lastEventSeq = -1,
            createdAt = "2026-09-27T00:00:00Z"
        )

        override suspend fun getLatestFirmware(productId: Int, hardwareVersion: String?): Result<OtaLatestResponse> =
            Result.success(latest)

        override suspend fun getActiveTask(deviceSn: String): Result<OtaTaskResponse?> = Result.success(activeTask)

        override suspend fun reserveTask(request: OtaTaskRequest): Result<OtaTaskResponse> {
            reservationCount++
            activeTask = task
            return Result.success(task)
        }

        override suspend fun getTask(taskId: String): Result<OtaTaskResponse> = Result.success(task)
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
        var lastMsgId: String? = null

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
            lastMsgId = msgId
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
        downloadUrl: String = "/download/speaker.bin",
        backendFirmwareId: String? = null
    ) = OtaLatestResponse(
        backendFirmwareId = backendFirmwareId,
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
