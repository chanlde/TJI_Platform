package com.tji.device.product.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCommandIdTest {
    @Test
    fun rapidAndParallelCommandsRemainUniqueAndReadable() {
        val ids = (1..2_000)
            .toList()
            .parallelStream()
            .map { DeviceCommandId.next("speaker", "volume") }
            .toList()

        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("speaker-volume-") })
    }
}
