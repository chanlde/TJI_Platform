package com.tji.device.product.firebucket.ui.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FireBucketControlAvailabilityTest {
    @Test
    fun linkAvailabilityGatesBucketControl() {
        assertTrue(canControlFireBucketSwitch(linkOnline = true))
        assertFalse(canControlFireBucketSwitch(linkOnline = false))
    }
}
