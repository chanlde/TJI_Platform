package com.tji.device.product.firebucket.repository

import com.tji.device.product.firebucket.model.FireBucketSwitchControlParams
import com.tji.device.product.firebucket.transport.CloudFireBucketControlTransport
import com.tji.device.product.firebucket.transport.FireBucketControlTransport
import com.tji.device.product.firebucket.transport.FireBucketSetServoCommand

/**
 * FireBucket 舵机控制命令边界。
 */
interface FireBucketSwitchRepository {
    suspend fun setAngle(linkSn: String, params: FireBucketSwitchControlParams)
}

class FireBucketSwitchCommandRepository(
    private val transport: () -> FireBucketControlTransport = { CloudFireBucketControlTransport() }
) : FireBucketSwitchRepository {
    override suspend fun setAngle(linkSn: String, params: FireBucketSwitchControlParams) {
        transport().setServo(
            FireBucketSetServoCommand(
                linkId = linkSn,
                deviceId = params.sn,
                angleDegrees = params.angle,
                speed = params.speed,
                mode = params.mode
            )
        )
    }
}
