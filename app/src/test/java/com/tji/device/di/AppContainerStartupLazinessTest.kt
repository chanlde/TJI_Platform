package com.tji.device.di

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AppContainerStartupLazinessTest {
    @Test
    fun mainViewModelFactoryDoesNotExpandProductOrMqttGraph() {
        assertNull(AppContainer.initializedMqttSubscriptionManagerOrNull())

        val factory = AppContainer.mainViewModelFactory

        assertNotNull(factory)
        assertNull(AppContainer.initializedMqttSubscriptionManagerOrNull())
    }
}
