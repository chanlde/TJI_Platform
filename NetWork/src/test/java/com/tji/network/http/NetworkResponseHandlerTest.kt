package com.tji.network.http

import com.tji.network.data.ApiResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class NetworkResponseHandlerTest {
    private val handler = NetworkResponseHandler(tag = "NetworkResponseHandlerTest")

    @Test
    fun cancellationIsPropagatedToCaller() = runBlocking {
        try {
            handler.safeApiCall<String> {
                throw CancellationException("cancel test")
            }
            fail("CancellationException should be rethrown")
        } catch (expected: CancellationException) {
            assertEquals("cancel test", expected.message)
        }
    }

    @Test
    fun missingServerMessageDoesNotBecomeLiteralNull() = runBlocking {
        val response = handler.safeApiCall {
            ApiResponse<String>(code = 500, message = null, data = null)
        }

        assertEquals(500, response.code)
        assertEquals("", response.message)
    }
}
