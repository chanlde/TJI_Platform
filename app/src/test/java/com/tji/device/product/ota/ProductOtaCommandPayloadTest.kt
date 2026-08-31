package com.tji.device.product.ota

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductOtaCommandPayloadTest {
    @Test
    fun startPayloadKeepsConfirmedNestedAndLegacyFields() {
        val payload = buildProductOtaStartPayload(
            serialNumber = "SPEAKER-01",
            msgId = "ota-123",
            timestampMillis = 1_700_000_000_000,
            packageInfo = ProductOtaPackage(
                targetVersion = "V1.0.2",
                targetInnerVersion = 12,
                hardwareVersion = "HW-A",
                downloadUrl = "https://www.tjinnovations.cloud/download/speaker.bin",
                fileSize = 216_256,
                sha256 = SHA256,
                signature = "signed-manifest"
            )
        )

        assertEquals(1, payload.getInt("v"))
        assertEquals("ota-123", payload.getString("msgId"))
        assertEquals("ota-123", payload.getString("cmdId"))
        assertEquals(1_700_000_000_000, payload.getLong("ts"))
        assertEquals(20, payload.getInt("cmd"))
        assertEquals("START_OTA", payload.getString("cmdName"))
        assertEquals("SPEAKER-01", payload.getString("deviceId"))

        val params = payload.getJSONObject("params")
        assertEquals("V1.0.2", params.getString("targetVersion"))
        assertEquals(12, params.getInt("targetInnerVersion"))
        assertEquals("HW-A", params.getString("hardwareVersion"))
        assertEquals(216_256, params.getLong("fileSize"))
        assertEquals(SHA256, params.getString("sha256"))
        assertEquals(
            "https://www.tjinnovations.cloud/download/speaker.bin",
            params.getString("downloadUrl")
        )
        assertEquals("signed-manifest", params.getString("signature"))

        assertEquals("V1.0.2", payload.getString("target_version"))
        assertEquals(12, payload.getInt("target_inner_version"))
        assertEquals("HW-A", payload.getString("hardware_version"))
        assertEquals(216_256, payload.getLong("file_size"))
        assertEquals(SHA256, payload.getString("sha256"))
        assertEquals("signed-manifest", payload.getString("signature"))
    }

    private companion object {
        const val SHA256 = "f51563a1db560764eda95a6f2f0c4fddfbcf7870efc6bcd5457025ee157b3509"
    }
}
