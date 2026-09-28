package com.tji.device.webControl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperWebAccessPolicyTest {
    @Test
    fun allowsOnlyKnownLanDeviceHostsOverHttp() {
        assertTrue(DeveloperWebAccessPolicy.isAllowed("http://192.168.5.1/index.html"))
        assertTrue(DeveloperWebAccessPolicy.isAllowed("http://192.168.5.10/admin/upload"))
        assertTrue(DeveloperWebAccessPolicy.isAllowed("http://192.168.5.10:80/status?tab=1"))
    }

    @Test
    fun rejectsExternalHostsAlternatePortsAndNonHttpSchemes() {
        listOf(
            "https://192.168.5.1/index.html",
            "http://192.168.5.1:8080/index.html",
            "http://192.168.5.11/index.html",
            "http://192.168.5.1@example.com/index.html",
            "file:///sdcard/secret.txt",
            "content://com.example.provider/item/1",
            "javascript:alert(1)",
            "data:text/html,hello",
            "http://example.com/"
        ).forEach { url ->
            assertFalse(url, DeveloperWebAccessPolicy.isAllowed(url))
        }
    }
}
