package com.tji.device.product.firebucket.transport

import android.util.Log
import com.tji.device.data.model.ProductType
import com.tji.device.product.firebucket.model.ControlMode
import com.tji.device.product.firebucket.mqtt.FireBucketMqttTopics
import com.tji.device.service.mqtt.ProductMqttRouter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class FireBucketSetServoCommand(
    val linkId: String,
    val deviceId: String,
    val angleDegrees: Int,
    val speed: Int,
    val mode: ControlMode
)

/** 控制页面只提交产品命令；云端 JSON 和直连二进制差异留在传输实现中。 */
fun interface FireBucketControlTransport {
    suspend fun setServo(command: FireBucketSetServoCommand)
}

class CloudFireBucketControlTransport : FireBucketControlTransport {
    override suspend fun setServo(command: FireBucketSetServoCommand) {
        val message = JSONObject().apply {
            put("event_type", "ServoControlRequest")
            put("serial_number", command.deviceId)
            put("angle", command.angleDegrees)
            put("speed", command.speed)
            put("mode", command.mode.name)
            put("timestamp", System.currentTimeMillis())
        }.toString()

        val topic = FireBucketMqttTopics.controlTopic(command.linkId)
        ProductMqttRouter.managerFor(ProductType.FireBucket).publishAwait(
            topic = topic,
            message = message,
            queueWhenDisconnected = false
        ).getOrThrow()
        Log.d(TAG, "${command.deviceId},控制指令发送成功: topic=$topic")
    }

    private companion object {
        const val TAG = "FireBucketCloudControl"
    }
}

enum class FireBucketConnectionMode {
    CLOUD,
    DIRECT_LINK
}

class FireBucketConnectionModeStore {
    private val _mode = MutableStateFlow(FireBucketConnectionMode.CLOUD)
    val mode: StateFlow<FireBucketConnectionMode> = _mode.asStateFlow()

    val current: FireBucketConnectionMode
        get() = _mode.value

    fun useCloud() {
        _mode.value = FireBucketConnectionMode.CLOUD
    }

    fun useDirectLink() {
        _mode.value = FireBucketConnectionMode.DIRECT_LINK
    }
}
