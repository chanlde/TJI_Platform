package com.tji.device.product.firebucket.repository

import android.util.Log
import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.model.FireBucketSwitchControlParams
import com.tji.device.product.firebucket.mqtt.FireBucketMqttTopics
import com.tji.device.service.mqtt.ProductMqttRouter
import org.json.JSONObject

/**
 * FireBucket 舵机控制命令边界。
 */
interface FireBucketSwitchRepository {
    suspend fun setAngle(linkSn: String, params: FireBucketSwitchControlParams)
}

class FireBucketSwitchCommandRepository : FireBucketSwitchRepository {
    override suspend fun setAngle(linkSn: String, params: FireBucketSwitchControlParams) {
        val message = JSONObject().apply {
            put("event_type", "ServoControlRequest")
            put("serial_number", params.sn)
            put("angle", params.angle)
            put("speed", params.speed)
            put("mode", params.mode.name)
            put("timestamp", System.currentTimeMillis())
        }.toString()

        val topic = FireBucketMqttTopics.controlTopic(linkSn)
        ProductMqttRouter.managerFor(ProductType.FireBucket).publishAwait(
            topic = topic,
            message = message,
            queueWhenDisconnected = false
        ).getOrThrow()
        Log.d(TAG, "${params.sn},控制指令发送成功: topic=$topic")
    }

    private companion object {
        const val TAG = "FireBucketSwitchCommand"
    }
}
