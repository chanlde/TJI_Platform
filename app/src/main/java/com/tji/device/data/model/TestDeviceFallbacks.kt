package com.tji.device.data.model

object TestDeviceFallbacks {
    const val SPEAKER_SERIAL = "TEWNHZDBK"
    const val DROPPER_SIX_STAGE_SERIAL = "D2234CF1E"

    val speaker: BoundAccountDevice
        get() = BoundAccountDevice(
            serialNumber = SPEAKER_SERIAL,
            name = "喊话器测试设备",
            productType = ProductType.Speaker
        )

    val dropperSixStage: BoundAccountDevice
        get() = BoundAccountDevice(
            serialNumber = DROPPER_SIX_STAGE_SERIAL,
            name = "FC100_FireDrop 测试设备",
            productType = ProductType.DropperSixStage
        )
}
