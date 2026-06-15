package com.tji.device.data.model

object TestDeviceFallbacks {
    const val SPEAKER_SERIAL = "TEWNHZDBK"
    const val DROPPER_SIX_STAGE_SERIAL = "D2234CF1E"

    val speaker: BoundAccountDevice
        get() = BoundAccountDevice(
            serialNumber = SPEAKER_SERIAL,
            name = "喊话器演示设备",
            productType = ProductType.Speaker
        )

    val dropperSixStage: BoundAccountDevice
        get() = BoundAccountDevice(
            serialNumber = DROPPER_SIX_STAGE_SERIAL,
            name = "六段抛投演示设备",
            productType = ProductType.DropperSixStage
        )

    fun demoDeviceFor(productType: ProductType): BoundAccountDevice? =
        when (productType) {
            ProductType.DropperSixStage -> dropperSixStage
            ProductType.Speaker -> speaker
            else -> null
        }
}
