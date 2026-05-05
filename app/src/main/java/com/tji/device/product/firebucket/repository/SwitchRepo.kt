package com.tji.device.product.firebucket.repository

import android.util.Log
import com.tji.device.product.firebucket.model.SwitchControlParms
import com.tji.device.product.firebucket.mqtt.FireBucketMqttTopics
import com.tji.network.MqttManager
import org.json.JSONObject

class SwitchRepo : FireBucketSwitchRepository {

    override suspend fun restart(sn: String) {}

    override suspend fun setAngle(linkSn: String, scParms: SwitchControlParms) {

        val message = JSONObject().apply {
            put("event_type", "ServoControlRequest")
            put("serial_number", scParms.sn)
            put("angle", scParms.angle)
            put("speed", scParms.speed)
            put("mode", scParms.mode.name)
            put("timestamp", System.currentTimeMillis())

        }.toString()

        val topic = FireBucketMqttTopics.controlTopic(linkSn)
        MqttManager.getInstance().publish(
            topic = topic,
            message = message,
            onSuccess = {
                Log.d(
                    "MainViewModel",
                    "${scParms.sn},控制指令发送成功: topic=$topic, message=$message"
                )
            },
            onError = { throwable ->
                Log.e("MainViewModel", "控制指令发送失败: ${throwable.message}")
            }
        )
    }
}
