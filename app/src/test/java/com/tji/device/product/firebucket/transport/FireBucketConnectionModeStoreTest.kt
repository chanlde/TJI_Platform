package com.tji.device.product.firebucket.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class FireBucketConnectionModeStoreTest {
    @Test
    fun defaultsTo4gAndPublishesEachControlModeChange() {
        val store = FireBucketConnectionModeStore()

        assertEquals(FireBucketConnectionMode.CLOUD, store.current)
        assertEquals(FireBucketConnectionMode.CLOUD, store.mode.value)

        store.useDirectLink()
        assertEquals(FireBucketConnectionMode.DIRECT_LINK, store.current)
        assertEquals(FireBucketConnectionMode.DIRECT_LINK, store.mode.value)

        store.useCloud()
        assertEquals(FireBucketConnectionMode.CLOUD, store.current)
        assertEquals(FireBucketConnectionMode.CLOUD, store.mode.value)
    }
}
