package com.tji.network.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OtaModelsGsonTest {
    @Test
    fun numericStringInnerVersionParsesAsInteger() {
        val latest = Gson().fromJson(
            """{"version":"V1.0.1","innerVersion":"11","type":2}""",
            OtaLatestResponse::class.java
        )

        assertEquals(11, latest.innerVersion)
    }

    @Test
    fun semanticInnerVersionDoesNotDiscardTheWholeResponse() {
        val latest = Gson().fromJson(
            """{"version":"V1.5.34.88","innerVersion":"V1.5.34.88","type":2}""",
            OtaLatestResponse::class.java
        )

        assertEquals("V1.5.34.88", latest.latestVersion)
        assertEquals(2, latest.type)
        assertNull(latest.innerVersion)
    }
}
