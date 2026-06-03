package com.tji.device.data.model

object TestDeviceFallbacks {
    const val SPEAKER_SERIAL = "SPEAKER-P2P"

    val speaker: BoundAccountDevice
        get() = BoundAccountDevice(
            serialNumber = SPEAKER_SERIAL,
            name = "喊话器测试设备",
            productType = ProductType.Speaker
        )
}
