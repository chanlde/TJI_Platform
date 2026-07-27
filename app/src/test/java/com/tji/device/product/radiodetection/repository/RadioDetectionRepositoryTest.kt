package com.tji.device.product.radiodetection.repository

import com.tji.device.product.radiodetection.protocol.RadioRidPacket
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RadioDetectionRepositoryTest {
    @Test
    fun targetsAreBoundedAndExpiredEntriesAreRemoved() = runBlocking {
        var now = 1_000L
        val repository = RadioDetectionRepo(
            nowMillis = { now },
            monotonicMillis = { now },
            targetTtlMillis = 100L,
            maxTargetsPerDevice = 2
        )

        repository.upsertRidPacket(SERIAL, packet("A"))
        now += 10
        repository.upsertRidPacket(SERIAL, packet("B"))
        now += 10
        repository.upsertRidPacket(SERIAL, packet("C"))

        assertEquals(listOf("C", "B"), repository.devices.value.single().targets.map { it.id })

        now += 200
        repository.upsertRidPacket(SERIAL, packet("D"))

        assertEquals(listOf("D"), repository.devices.value.single().targets.map { it.id })
    }

    @Test
    fun expiredTargetsAreRemovedEvenWhenNoNewPacketArrives() = runBlocking {
        var now = 1_000L
        val repository = RadioDetectionRepo(
            nowMillis = { now },
            monotonicMillis = { now },
            targetTtlMillis = 100L
        )
        repository.upsertRidPacket(SERIAL, packet("A"))

        now += 101
        repository.pruneExpiredTargets()

        assertEquals(emptyList<String>(), repository.devices.value.single().targets.map { it.id })
    }

    @Test
    fun updatedTargetMovesToFrontWithoutDuplicatingEntry() = runBlocking {
        var now = 1_000L
        val repository = RadioDetectionRepo(
            nowMillis = { now },
            monotonicMillis = { now }
        )

        repository.upsertRidPacket(SERIAL, packet("A"))
        now += 10
        repository.upsertRidPacket(SERIAL, packet("B"))
        now += 10
        repository.upsertRidPacket(SERIAL, packet("A"))

        assertEquals(listOf("A", "B"), repository.devices.value.single().targets.map { it.id })
    }

    @Test
    fun outOfOrderPacketCannotRollBackNewerTargetStateOrReviveItsTtl() = runBlocking {
        var now = 1_000L
        val repository = RadioDetectionRepo(
            nowMillis = { now },
            monotonicMillis = { now },
            targetTtlMillis = 100L
        )
        repository.upsertRidPacket(
            SERIAL,
            packet(
                id = "A",
                droneLatitude = 31.2,
                headingDegrees = 90,
                timestampMillis = 2_000
            )
        )
        val newerLatitude = repository.devices.value.single().targets.single().latitude

        now += 50
        repository.upsertRidPacket(
            SERIAL,
            packet(
                id = "A",
                droneLatitude = 20.0,
                headingDegrees = 10,
                timestampMillis = 1_000
            )
        )

        val state = repository.devices.value.single()
        val target = state.targets.single()
        assertEquals(newerLatitude, target.latitude, 0.000001)
        assertEquals(90, target.headingDegrees)
        assertEquals(1_000L, target.lastSeenAtMillis)
        assertEquals(2_000L, target.sourceTimestampMillis)
        assertEquals(1, state.filteredMessageCount)

        now += 51
        repository.pruneExpiredTargets()
        assertEquals(emptyList<String>(), repository.devices.value.single().targets.map { it.id })
    }

    @Test
    fun expiredTargetDoesNotRejectFirstPacketAfterDeviceClockReset() = runBlocking {
        var now = 1_000L
        val repository = RadioDetectionRepo(
            nowMillis = { now },
            monotonicMillis = { now },
            targetTtlMillis = 100L
        )
        repository.upsertRidPacket(
            SERIAL,
            packet(id = "A", headingDegrees = 90, timestampMillis = 20_000)
        )

        now += 101
        repository.upsertRidPacket(
            SERIAL,
            packet(id = "A", headingDegrees = 10, timestampMillis = 5)
        )

        val state = repository.devices.value.single()
        assertEquals(listOf("A"), state.targets.map { it.id })
        assertEquals(10, state.targets.single().headingDegrees)
        assertEquals(5L, state.targets.single().sourceTimestampMillis)
        assertEquals(0, state.filteredMessageCount)
    }

    @Test
    fun incompleteOperatorCoordinateCannotOverwriteLastUsableDevicePosition() = runBlocking {
        val repository = RadioDetectionRepo(
            nowMillis = { 1_000L },
            monotonicMillis = { 1_000L }
        )
        repository.upsertRidPacket(
            SERIAL,
            packet(id = "A", operatorLatitude = 31.1, operatorLongitude = 121.1)
        )
        val usable = repository.devices.value.single().currentCoordinate

        repository.upsertRidPacket(
            SERIAL,
            packet(id = "B", operatorLatitude = 31.2, operatorLongitude = 181.0)
        )

        assertEquals(usable, repository.devices.value.single().currentCoordinate)
    }

    @Test
    fun wallClockRollbackCannotKeepExpiredTargetAlive() = runBlocking {
        var wallClockMillis = 1_700_000_000_000L
        var elapsedMillis = 1_000L
        val repository = RadioDetectionRepo(
            nowMillis = { wallClockMillis },
            monotonicMillis = { elapsedMillis },
            targetTtlMillis = 100L
        )
        repository.upsertRidPacket(SERIAL, packet("A"))

        wallClockMillis -= 60_000L
        elapsedMillis += 101L
        repository.pruneExpiredTargets()

        assertEquals(emptyList<String>(), repository.devices.value.single().targets.map { it.id })
    }

    @Test
    fun wallClockJumpForwardCannotExpireFreshTarget() = runBlocking {
        var wallClockMillis = 1_700_000_000_000L
        var elapsedMillis = 1_000L
        val repository = RadioDetectionRepo(
            nowMillis = { wallClockMillis },
            monotonicMillis = { elapsedMillis },
            targetTtlMillis = 100L
        )
        repository.upsertRidPacket(SERIAL, packet("A"))

        wallClockMillis += 24 * 60 * 60 * 1_000L
        elapsedMillis += 50L
        repository.pruneExpiredTargets()

        assertEquals(listOf("A"), repository.devices.value.single().targets.map { it.id })
    }

    private fun packet(
        id: String,
        droneLatitude: Double = 31.0,
        headingDegrees: Int = 0,
        timestampMillis: Long? = null,
        operatorLatitude: Double = 31.1,
        operatorLongitude: Double = 121.1
    ) = RadioRidPacket(
        sourceDeviceId = SERIAL,
        targetId = id,
        rssi = -60,
        frequencyCode = null,
        frequencyLabel = "2.4GHz",
        uaType = 2,
        status = null,
        headingDegrees = headingDegrees,
        speedMetersPerSecond = 0.0,
        verticalSpeedMetersPerSecond = null,
        droneLongitude = 121.0,
        droneLatitude = droneLatitude,
        altitudeGeoMeters = 30.0,
        altitudeBaroMeters = null,
        heightMeters = 30.0,
        operatorLongitude = operatorLongitude,
        operatorLatitude = operatorLatitude,
        operatorAltitudeMeters = 0.0,
        timestampMillis = timestampMillis
    )

    private companion object {
        const val SERIAL = "RADIO-001"
    }
}
