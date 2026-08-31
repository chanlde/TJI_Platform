package com.tji.device.service

import com.tji.device.data.model.ProductType
import com.tji.network.MqttClientGateway
import com.tji.network.MqttConnectionConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class MqttSubscriptionManagerTest {
    @Test
    fun unsubscribeWaitsForBrokerAndRemovesActualTarget() = runBlocking {
        val gateway = RecordingMqttGateway()
        val manager = MqttSubscriptionManager(
            mqttEventHandler = RecordingMessageHandler(),
            clientFor = { gateway }
        )

        manager.subscribeToDevices(listOf(SERIAL), ProductType.Speaker)
        manager.unsubscribeFromTargets(listOf(SubscriptionTarget(SERIAL, ProductType.Speaker)))

        assertEquals(2, gateway.subscribedTopics.size)
        assertEquals(gateway.subscribedTopics.toSet(), gateway.unsubscribedTopics.toSet())
        assertTrue(manager.getSubscribedTargets().isEmpty())
        manager.cleanup()
    }

    @Test
    fun lifecycleAndSharedStatusTopicsAreSubscribedAtQosOne() = runBlocking {
        val gateway = RecordingMqttGateway()
        val manager = MqttSubscriptionManager(
            mqttEventHandler = RecordingMessageHandler(),
            clientFor = { gateway }
        )

        manager.subscribeToDevices(listOf(SERIAL), ProductType.Speaker)

        assertEquals(listOf(1, 1), gateway.subscribedQos)
        manager.cleanup()
    }

    @Test
    fun fireDropSubscribesToActualAndCompatibilityTopicPrefixes() = runBlocking {
        val gateway = RecordingMqttGateway()
        val manager = MqttSubscriptionManager(
            mqttEventHandler = RecordingMessageHandler(),
            clientFor = { gateway }
        )

        manager.subscribeToDevices(listOf("D29D5405F"), ProductType.DropperSixStage)

        assertEquals(
            listOf(
                "FC100_FireDrop/devices/D29D5405F/lifecycle",
                "SixStageDropper/devices/D29D5405F/lifecycle",
                "FC100_FireDrop/devices/D29D5405F/status",
                "SixStageDropper/devices/D29D5405F/status"
            ),
            gateway.subscribedTopics
        )
        assertEquals(listOf(1, 1, 1, 1), gateway.subscribedQos)
        manager.cleanup()
    }

    @Test
    fun sharedStatusPayloadClassificationSeparatesAckFromTelemetry() {
        assertEquals(false, isPriorityStatusMessage("""{"type":"state","battery":80}"""))
        assertEquals(false, isPriorityStatusMessage("""{"type":"status","battery":80}"""))
        assertEquals(true, isPriorityStatusMessage("""{"type":"ack","msgId":"cmd-1"}"""))
        assertEquals(true, isPriorityStatusMessage("""{"type":"record_list","total":0}"""))
        assertEquals(true, isPriorityStatusMessage("""{"event_type":"otaStatus","progress":50}"""))
        assertEquals(false, isPriorityStatusMessage("not-json"))
    }

    @Test
    fun clearWaitsForSubackAndThenUnsubscribesTheCompletedTarget() = runBlocking {
        val subscribeGate = CompletableDeferred<Unit>()
        val firstSubscribeStarted = CompletableDeferred<Unit>()
        val gateway = RecordingMqttGateway(
            subscribeGate = subscribeGate,
            firstSubscribeStarted = firstSubscribeStarted
        )
        val manager = MqttSubscriptionManager(
            mqttEventHandler = RecordingMessageHandler(),
            clientFor = { gateway }
        )

        val subscribeJob = launch {
            manager.subscribeToDevices(listOf(SERIAL), ProductType.Speaker)
        }
        firstSubscribeStarted.await()
        val clearJob = launch {
            manager.clearAllSubscriptions()
        }
        subscribeGate.complete(Unit)
        subscribeJob.join()
        clearJob.join()

        assertTrue(manager.getSubscribedTargets().isEmpty())
        assertEquals(2, gateway.unsubscribedTopics.size)
        manager.cleanup()
    }

    @Test
    fun cleanupDuringSubackPreventsLateSubscriptionFromReviving() = runBlocking {
        val subscribeGate = CompletableDeferred<Unit>()
        val firstSubscribeStarted = CompletableDeferred<Unit>()
        val gateway = RecordingMqttGateway(
            subscribeGate = subscribeGate,
            firstSubscribeStarted = firstSubscribeStarted
        )
        val handler = RecordingMessageHandler()
        val manager = MqttSubscriptionManager(
            mqttEventHandler = handler,
            clientFor = { gateway }
        )

        val subscribeJob = launch {
            manager.subscribeToDevices(listOf(SERIAL), ProductType.Speaker)
        }
        firstSubscribeStarted.await()
        manager.cleanup()
        gateway.deliver(gateway.subscribedTopics.single(), "stale-after-cleanup", false)
        delay(20L)
        assertTrue(handler.messages.isEmpty())
        subscribeGate.complete(Unit)
        subscribeJob.join()

        assertTrue(manager.getSubscribedTargets().isEmpty())
        assertEquals(1, gateway.unsubscribedTopics.size)
        manager.cleanup()
    }

    @Test
    fun cancellationAfterPartialSubackStillRollsBackBrokerSubscription() = runBlocking {
        val secondSubscribeGate = CompletableDeferred<Unit>()
        val secondSubscribeStarted = CompletableDeferred<Unit>()
        val gateway = RecordingMqttGateway(
            secondSubscribeGate = secondSubscribeGate,
            secondSubscribeStarted = secondSubscribeStarted,
            unsubscribeChecksCancellation = true
        )
        val manager = MqttSubscriptionManager(
            mqttEventHandler = RecordingMessageHandler(),
            clientFor = { gateway }
        )

        val subscribeJob = launch {
            manager.subscribeToDevices(listOf(SERIAL), ProductType.Speaker)
        }
        secondSubscribeStarted.await()
        subscribeJob.cancelAndJoin()

        assertEquals(
            listOf(gateway.subscribedTopics.first()),
            gateway.unsubscribedTopics
        )
        assertTrue(manager.getSubscribedTargets().isEmpty())
        manager.cleanup()
    }

    @Test
    fun cancellationDuringClearStillRemovesBrokerAndLocalSubscriptions() = runBlocking {
        val unsubscribeGate = CompletableDeferred<Unit>()
        val unsubscribeStarted = CompletableDeferred<Unit>()
        val gateway = RecordingMqttGateway(
            unsubscribeGate = unsubscribeGate,
            unsubscribeStarted = unsubscribeStarted,
            unsubscribeChecksCancellation = true
        )
        val manager = MqttSubscriptionManager(
            mqttEventHandler = RecordingMessageHandler(),
            clientFor = { gateway }
        )
        manager.subscribeToDevices(listOf(SERIAL), ProductType.Speaker)

        val clearJob = launch {
            manager.clearAllSubscriptions()
        }
        unsubscribeStarted.await()
        clearJob.cancel()
        unsubscribeGate.complete(Unit)
        clearJob.join()

        assertTrue(clearJob.isCancelled)
        assertEquals(
            gateway.subscribedTopics.toSet(),
            gateway.unsubscribedTopics.toSet()
        )
        assertTrue(manager.getSubscribedTargets().isEmpty())
        manager.cleanup()
    }

    @Test
    fun messagesForOneTargetAreHandledInReceiveOrder() = runBlocking {
        val handler = RecordingMessageHandler(expectedMessages = 2)
        val gateway = RecordingMqttGateway()
        val manager = MqttSubscriptionManager(
            mqttEventHandler = handler,
            clientFor = { gateway }
        )

        manager.subscribeToDevices(listOf(SERIAL), ProductType.Speaker)
        val statusTopic = gateway.subscribedTopics.last()
        gateway.deliver(statusTopic, "first", false)
        gateway.deliver(statusTopic, "second", true)

        withTimeout(2_000L) {
            handler.completed.await()
        }
        assertEquals(listOf("first", "second"), handler.messages.map { it.payload })
        assertEquals(listOf(false, true), handler.messages.map { it.isRetained })
        manager.cleanup()
    }

    @Test
    fun clearCancelsAndJoinsStartedHandlerBeforeReturning() = runBlocking {
        val handler = CancellationCommitHandler()
        val gateway = RecordingMqttGateway()
        val manager = MqttSubscriptionManager(
            mqttEventHandler = handler,
            clientFor = { gateway }
        )
        manager.subscribeToDevices(listOf(SERIAL), ProductType.Speaker)
        gateway.deliver(gateway.subscribedTopics.last(), "old-session", false)
        handler.started.await()

        manager.clearAllSubscriptions()

        assertTrue(handler.finished.isCompleted)
        assertEquals(listOf("old-session"), handler.finalCommits)
        val commitsAtReturn = handler.finalCommits.toList()
        delay(20L)
        assertEquals(commitsAtReturn, handler.finalCommits)
        manager.cleanup()
    }

    @Test
    fun cleanSessionReconnectRestoresDesiredSubscriptions() = runBlocking {
        val gateway = RecordingMqttGateway(initialConnectionEpoch = 1L)
        val manager = MqttSubscriptionManager(
            mqttEventHandler = RecordingMessageHandler(),
            clientFor = { gateway }
        )

        manager.reconcileSubscriptions(setOf(SubscriptionTarget(SERIAL, ProductType.Speaker)))
        delay(50)
        gateway.reconnect()

        withTimeout(2_000L) {
            while (gateway.subscribedTopics.size < 4) delay(10)
        }

        assertEquals(4, gateway.subscribedTopics.size)
        assertEquals(listOf(SERIAL), manager.getSubscribedDevices(ProductType.Speaker))
        manager.cleanup()
    }

    @Test
    fun emptyDesiredTargetsStopsUnusedConnectionObserver() = runBlocking {
        val gateway = RecordingMqttGateway(initialConnectionEpoch = 1L)
        val manager = MqttSubscriptionManager(
            mqttEventHandler = RecordingMessageHandler(),
            clientFor = { gateway }
        )

        manager.reconcileSubscriptions(setOf(SubscriptionTarget(SERIAL, ProductType.Speaker)))
        assertEquals(1, manager.connectionObserverCount())

        manager.reconcileSubscriptions(emptySet())

        assertEquals(0, manager.connectionObserverCount())
        manager.cleanup()
    }

    @Test
    fun switchingBrokerReplacesConnectionObserverInsteadOfAccumulatingIt() = runBlocking {
        val platformGateway = RecordingMqttGateway(initialConnectionEpoch = 1L)
        val legacyGateway = RecordingMqttGateway(initialConnectionEpoch = 1L)
        val manager = MqttSubscriptionManager(
            mqttEventHandler = RecordingMessageHandler(),
            clientFor = { productType ->
                if (productType == ProductType.RadioDetection) legacyGateway else platformGateway
            }
        )

        manager.reconcileSubscriptions(setOf(SubscriptionTarget(SERIAL, ProductType.Speaker)))
        manager.reconcileSubscriptions(
            setOf(SubscriptionTarget("RADIO-001", ProductType.RadioDetection))
        )

        assertEquals(1, manager.connectionObserverCount())
        manager.cleanup()
    }

    @Test
    fun failedBrokerUnsubscribeStillClearsLocalSessionState() = runBlocking {
        val gateway = RecordingMqttGateway()
        val manager = MqttSubscriptionManager(
            mqttEventHandler = RecordingMessageHandler(),
            clientFor = { gateway }
        )
        manager.reconcileSubscriptions(setOf(SubscriptionTarget(SERIAL, ProductType.Speaker)))
        gateway.failUnsubscribe = true

        val failure = runCatching { manager.clearAllSubscriptions() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(manager.getSubscribedTargets().isEmpty())
        gateway.failUnsubscribe = false
        manager.reconcileSubscriptions(setOf(SubscriptionTarget(SERIAL, ProductType.Speaker)))
        assertEquals(4, gateway.subscribedTopics.size)
        manager.cleanup()
    }

    @Test
    fun reliableAckIsNotDroppedByTelemetryBurst() = runBlocking {
        val handler = BlockingMessageHandler()
        val gateway = RecordingMqttGateway()
        val manager = MqttSubscriptionManager(
            mqttEventHandler = handler,
            clientFor = { gateway }
        )
        manager.subscribeToDevices(listOf(SERIAL), ProductType.RadioDetection)
        val statusTopic = gateway.subscribedTopics[1]
        val ackTopic = gateway.subscribedTopics[2]

        gateway.deliver(statusTopic, "telemetry-blocking", false)
        handler.firstMessageStarted.await()
        gateway.deliver(ackTopic, "reliable-ack", false)
        repeat(400) { gateway.deliver(statusTopic, "telemetry-$it", false) }
        handler.releaseFirstMessage.complete(Unit)

        withTimeout(2_000L) { handler.reliableAckReceived.await() }
        manager.cleanup()
    }

    @Test
    fun ackOnSharedStatusTopicRunsBeforeQueuedTelemetry() = runBlocking {
        val handler = SharedStatusPriorityHandler()
        val gateway = RecordingMqttGateway()
        val manager = MqttSubscriptionManager(
            mqttEventHandler = handler,
            clientFor = { gateway }
        )
        manager.subscribeToDevices(listOf(SERIAL), ProductType.Speaker)
        val statusTopic = gateway.subscribedTopics[1]
        val blockingState = """{"type":"state","seq":0}"""
        val ack = """{"type":"ack","msgId":"cmd-1"}"""

        gateway.deliver(statusTopic, blockingState, false)
        handler.firstMessageStarted.await()
        repeat(300) { sequence ->
            gateway.deliver(statusTopic, """{"type":"state","seq":${sequence + 1}}""", false)
        }
        gateway.deliver(statusTopic, ack, false)
        handler.releaseFirstMessage.complete(Unit)

        withTimeout(2_000L) { handler.ackReceived.await() }
        assertEquals(listOf(blockingState, ack), handler.firstTwoMessages)
        manager.cleanup()
    }

    @Test
    fun reliableQueueIsBoundedAndKeepsNewestMessagesDuringHandlerStall() = runBlocking {
        val handler = BoundedReliableQueueHandler()
        val gateway = RecordingMqttGateway()
        val manager = MqttSubscriptionManager(
            mqttEventHandler = handler,
            clientFor = { gateway }
        )
        manager.subscribeToDevices(listOf(SERIAL), ProductType.RadioDetection)
        val statusTopic = gateway.subscribedTopics[1]
        val ackTopic = gateway.subscribedTopics[2]

        gateway.deliver(statusTopic, "telemetry-blocking", false)
        handler.firstMessageStarted.await()
        repeat(400) { gateway.deliver(ackTopic, "reliable-$it", false) }
        handler.releaseFirstMessage.complete(Unit)

        withTimeout(2_000L) { handler.lastReliableMessageReceived.await() }
        assertEquals(256, handler.reliableMessages.size)
        assertEquals("reliable-144", handler.reliableMessages.first())
        assertEquals("reliable-399", handler.reliableMessages.last())
        manager.cleanup()
    }

    private class RecordingMqttGateway(
        private val subscribeGate: CompletableDeferred<Unit>? = null,
        private val firstSubscribeStarted: CompletableDeferred<Unit>? = null,
        private val secondSubscribeGate: CompletableDeferred<Unit>? = null,
        private val secondSubscribeStarted: CompletableDeferred<Unit>? = null,
        private val unsubscribeGate: CompletableDeferred<Unit>? = null,
        private val unsubscribeStarted: CompletableDeferred<Unit>? = null,
        private val unsubscribeChecksCancellation: Boolean = false,
        initialConnectionEpoch: Long = 0L
    ) : MqttClientGateway {
        private val mutableConnectionEpoch = MutableStateFlow(initialConnectionEpoch)
        override val connectionEpoch: StateFlow<Long> = mutableConnectionEpoch
        val subscribedTopics: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val subscribedQos: MutableList<Int> = Collections.synchronizedList(mutableListOf())
        val unsubscribedTopics: MutableList<String> = Collections.synchronizedList(mutableListOf())
        private val callbacks = mutableMapOf<String, (String, Boolean) -> Unit>()
        var failUnsubscribe = false

        override fun getConfig(): MqttConnectionConfig = MqttConnectionConfig.default()

        override suspend fun subscribeAwait(
            topic: String,
            qos: Int,
            onMessage: (message: String, isRetained: Boolean) -> Unit
        ): Result<Unit> {
            val subscriptionIndex = subscribedTopics.size
            subscribedTopics += topic
            subscribedQos += qos
            callbacks[topic] = onMessage
            if (subscriptionIndex == 0) {
                firstSubscribeStarted?.complete(Unit)
                subscribeGate?.await()
            } else if (subscriptionIndex == 1) {
                secondSubscribeStarted?.complete(Unit)
                secondSubscribeGate?.await()
            }
            return Result.success(Unit)
        }

        override suspend fun unsubscribeAwait(topic: String): Result<Unit> {
            unsubscribeStarted?.complete(Unit)
            unsubscribeGate?.await()
            if (unsubscribeChecksCancellation) {
                currentCoroutineContext().ensureActive()
            }
            if (failUnsubscribe) return Result.failure(IllegalStateException("offline"))
            unsubscribedTopics += topic
            callbacks.remove(topic)
            return Result.success(Unit)
        }

        override suspend fun publishAwait(
            topic: String,
            message: String,
            qos: Int,
            retain: Boolean,
            queueWhenDisconnected: Boolean
        ): Result<Unit> = Result.success(Unit)

        fun deliver(topic: String, message: String, isRetained: Boolean) {
            callbacks.getValue(topic)(message, isRetained)
        }

        fun reconnect() {
            mutableConnectionEpoch.value += 1
        }
    }

    private class RecordingMessageHandler(
        expectedMessages: Int = 0
    ) : MqttMessageHandler {
        val messages = mutableListOf<ReceivedMessage>()
        val completed = CompletableDeferred<Unit>()
        private val expectedMessages = expectedMessages

        override suspend fun handleMessage(
            serialNumber: String,
            productType: ProductType,
            message: String,
            isRetained: Boolean
        ) {
            if (message == "first") delay(20)
            messages += ReceivedMessage(message, isRetained)
            if (messages.size == expectedMessages) completed.complete(Unit)
        }

        override fun cleanup() = Unit
    }

    private class BlockingMessageHandler : MqttMessageHandler {
        val firstMessageStarted = CompletableDeferred<Unit>()
        val releaseFirstMessage = CompletableDeferred<Unit>()
        val reliableAckReceived = CompletableDeferred<Unit>()

        override suspend fun handleMessage(
            serialNumber: String,
            productType: ProductType,
            message: String,
            isRetained: Boolean
        ) {
            if (message == "telemetry-blocking") {
                firstMessageStarted.complete(Unit)
                releaseFirstMessage.await()
            }
            if (message == "reliable-ack") {
                reliableAckReceived.complete(Unit)
            }
        }

        override fun cleanup() = Unit
    }

    private class CancellationCommitHandler : MqttMessageHandler {
        val started = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        val finalCommits = mutableListOf<String>()

        override suspend fun handleMessage(
            serialNumber: String,
            productType: ProductType,
            message: String,
            isRetained: Boolean
        ) {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    finalCommits += message
                    finished.complete(Unit)
                }
            }
        }

        override fun cleanup() = Unit
    }

    private class BoundedReliableQueueHandler : MqttMessageHandler {
        val firstMessageStarted = CompletableDeferred<Unit>()
        val releaseFirstMessage = CompletableDeferred<Unit>()
        val lastReliableMessageReceived = CompletableDeferred<Unit>()
        val reliableMessages = mutableListOf<String>()

        override suspend fun handleMessage(
            serialNumber: String,
            productType: ProductType,
            message: String,
            isRetained: Boolean
        ) {
            if (message == "telemetry-blocking") {
                firstMessageStarted.complete(Unit)
                releaseFirstMessage.await()
            } else if (message.startsWith("reliable-")) {
                reliableMessages += message
                if (message == "reliable-399") {
                    lastReliableMessageReceived.complete(Unit)
                }
            }
        }

        override fun cleanup() = Unit
    }

    private class SharedStatusPriorityHandler : MqttMessageHandler {
        val firstMessageStarted = CompletableDeferred<Unit>()
        val releaseFirstMessage = CompletableDeferred<Unit>()
        val ackReceived = CompletableDeferred<Unit>()
        val firstTwoMessages = mutableListOf<String>()

        override suspend fun handleMessage(
            serialNumber: String,
            productType: ProductType,
            message: String,
            isRetained: Boolean
        ) {
            if (firstTwoMessages.size < 2) firstTwoMessages += message
            if (message == """{"type":"state","seq":0}""") {
                firstMessageStarted.complete(Unit)
                releaseFirstMessage.await()
            }
            if (message == """{"type":"ack","msgId":"cmd-1"}""") {
                ackReceived.complete(Unit)
            }
        }

        override fun cleanup() = Unit
    }

    private data class ReceivedMessage(
        val payload: String,
        val isRetained: Boolean
    )

    private companion object {
        const val SERIAL = "SPEAKER-001"
    }
}
