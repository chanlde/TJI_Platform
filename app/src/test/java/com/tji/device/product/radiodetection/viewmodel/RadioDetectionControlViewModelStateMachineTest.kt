package com.tji.device.product.radiodetection.viewmodel

import com.tji.device.MainDispatcherRule
import com.tji.device.product.radiodetection.model.RadioRgbAck
import com.tji.device.product.radiodetection.model.RadioRgbColor
import com.tji.device.product.radiodetection.model.RadioRgbCommand
import com.tji.device.product.radiodetection.model.RadioRgbMode
import com.tji.device.product.radiodetection.protocol.RadioRidPacket
import com.tji.device.product.radiodetection.replay.RadioDetectionReplayStore
import com.tji.device.product.radiodetection.replay.RadioReplayPersistence
import com.tji.device.product.radiodetection.replay.RadioReplayRecord
import com.tji.device.product.radiodetection.repository.RadioDetectionControlRepository
import com.tji.device.product.radiodetection.repository.RadioDetectionDeviceState
import com.tji.device.product.radiodetection.repository.RadioDetectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RadioDetectionControlViewModelStateMachineTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun rgbCommandTransitionsFromPendingThroughAckThenClears() {
        val repository = FakeRadioRepository(onlineDevice(SERIAL))
        val control = RecordingControlRepository()
        val viewModel = RadioDetectionControlViewModel(repository, control, replayStore())
        scheduler.runCurrent()

        viewModel.sendRgbCommand(
            serialNumber = SERIAL,
            mode = RadioRgbMode.Steady,
            color = RadioRgbColor.Red,
            brightness = 80,
            speed = null,
            save = false
        )
        val msgId = requireNotNull(control.command).msgId
        assertTrue(viewModel.rgbFeedback.value?.pending == true)

        control.onSuccess?.invoke()
        scheduler.runCurrent()
        assertEquals("指令已发送，等待设备确认", viewModel.rgbFeedback.value?.text)

        repository.emitAck(msgId, ok = true)
        scheduler.runCurrent()
        assertEquals(true, viewModel.rgbFeedback.value?.success)

        scheduler.advanceTimeBy(2_400)
        scheduler.runCurrent()
        assertNull(viewModel.rgbFeedback.value)
        viewModel.unbindDevice(SERIAL)
    }

    @Test
    fun callbackFromOlderCommandCannotOverwriteNewerFeedback() {
        val repository = FakeRadioRepository(onlineDevice(SERIAL))
        val control = RecordingControlRepository()
        val viewModel = RadioDetectionControlViewModel(repository, control, replayStore())
        scheduler.runCurrent()

        viewModel.sendRgbCommand(SERIAL, RadioRgbMode.Steady, RadioRgbColor.Red, 80, null, false)
        val oldError = requireNotNull(control.onError)
        viewModel.sendRgbCommand(SERIAL, RadioRgbMode.Steady, RadioRgbColor.Green, 70, null, true)
        val currentMsgId = requireNotNull(control.command).msgId

        oldError(IllegalStateException("old publish failed"))
        scheduler.runCurrent()

        assertEquals(currentMsgId, viewModel.rgbFeedback.value?.msgId)
        assertTrue(viewModel.rgbFeedback.value?.pending == true)
        viewModel.unbindDevice(SERIAL)
    }

    @Test
    fun unbindStopsPruningAndRejectsLateCommandCallbacks() {
        val repository = FakeRadioRepository(onlineDevice(SERIAL))
        val control = RecordingControlRepository()
        val viewModel = RadioDetectionControlViewModel(repository, control, replayStore())
        viewModel.bindDevice(SERIAL)
        scheduler.advanceTimeBy(5_000)
        scheduler.runCurrent()
        assertEquals(1, repository.pruneCount)

        viewModel.sendRgbCommand(SERIAL, RadioRgbMode.Steady, RadioRgbColor.Red, 80, null, false)
        val lateSuccess = requireNotNull(control.onSuccess)
        viewModel.unbindDevice(SERIAL)
        lateSuccess()
        scheduler.advanceTimeBy(15_000)
        scheduler.runCurrent()

        assertEquals(1, repository.pruneCount)
        assertNull(viewModel.rgbFeedback.value)
    }

    private val scheduler
        get() = mainDispatcherRule.dispatcher.scheduler

    private fun replayStore() = RadioDetectionReplayStore(
        persistence = object : RadioReplayPersistence {
            override fun read(serialNumber: String): RadioReplayRecord? = null
            override fun write(serialNumber: String, replay: RadioReplayRecord) = Unit
            override fun remove(serialNumber: String) = Unit
            override fun prune(olderThanMillis: Long, maxEntries: Int) = Unit
            override fun clearAll() = Unit
        },
        persistenceScope = CoroutineScope(mainDispatcherRule.dispatcher)
    )

    private class RecordingControlRepository : RadioDetectionControlRepository {
        var command: RadioRgbCommand? = null
        var onSuccess: (() -> Unit)? = null
        var onError: ((Throwable) -> Unit)? = null

        override fun sendRgbCommand(
            serialNumber: String,
            command: RadioRgbCommand,
            onSuccess: (() -> Unit)?,
            onError: ((Throwable) -> Unit)?
        ) {
            this.command = command
            this.onSuccess = onSuccess
            this.onError = onError
        }
    }

    private class FakeRadioRepository(initial: RadioDetectionDeviceState) : RadioDetectionRepository {
        private val mutableDevices = MutableStateFlow(listOf(initial))
        override val devices: StateFlow<List<RadioDetectionDeviceState>> = mutableDevices
        var pruneCount: Int = 0

        fun emitAck(msgId: String, ok: Boolean) {
            val ack = RadioRgbAck(
                msgId = msgId,
                ok = ok,
                code = if (ok) 0 else 121,
                message = if (ok) "rgb applied" else "bad params",
                timestamp = null
            )
            mutableDevices.value = mutableDevices.value.map { it.copy(rgbAck = ack) }
        }

        override suspend fun upsertRidPacket(serialNumber: String, packet: RadioRidPacket) = Unit
        override suspend fun updatePayloadStatus(serialNumber: String, payloadStatus: String, filtered: Boolean) = Unit
        override suspend fun updateOnlineStatus(serialNumber: String, isOnline: Boolean) = Unit
        override suspend fun updateRgbAck(serialNumber: String, ack: RadioRgbAck) = Unit
        override suspend fun pruneExpiredTargets() {
            pruneCount += 1
        }
        override fun clearDevices() {
            mutableDevices.value = emptyList()
        }
    }

    private companion object {
        const val SERIAL = "RADIO-01"

        fun onlineDevice(serialNumber: String) = RadioDetectionDeviceState(
            serialNumber = serialNumber,
            displayName = "频谱检测仪",
            isOnline = true,
            payloadStatus = "在线",
            currentCoordinate = null,
            targets = emptyList(),
            lastUpdateMillis = null
        )
    }
}
