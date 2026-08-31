package com.tji.device.data.repository

import com.tji.network.TjiApiGateway
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class NetworkAuthRepositoryTest {
    @Before
    fun setUp() {
        TjiApiGateway.authToken = "session-token"
    }

    @After
    fun tearDown() {
        TjiApiGateway.clearAuthToken()
    }

    @Test
    fun logoutClearsGatewayAuthenticationToken() = runTest {
        NetworkAuthRepository().logout()

        assertNull(TjiApiGateway.authToken)
    }
}
