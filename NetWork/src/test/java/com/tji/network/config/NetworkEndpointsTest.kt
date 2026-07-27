package com.tji.network.config

import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkEndpointsTest {
    @Test
    fun baseUrlWithoutTrailingSlashIsNormalized() {
        assertEquals(
            "https://example.test/api/",
            normalizeBaseUrl("https://example.test/api")
        )
    }

    @Test
    fun existingTrailingSlashIsNotDuplicated() {
        assertEquals(
            "https://example.test/api/",
            normalizeBaseUrl("https://example.test/api/")
        )
    }
}
