package com.tji.device.product.radiodetection.repository

import android.util.Log
import com.tji.device.data.model.ProductType
import com.tji.device.product.radiodetection.model.RadioRgbCommand
import com.tji.device.product.radiodetection.mqtt.RadioDetectionMqttTopics
import com.tji.device.product.radiodetection.mqtt.toMqttJson
import com.tji.device.service.mqtt.ProductMqttRouter

interface RadioDetectionControlRepository {
    fun sendRgbCommand(
        serialNumber: String,
        command: RadioRgbCommand,
        onSuccess: (() -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    )
}

class RadioDetectionControlRepo : RadioDetectionControlRepository {

    override fun sendRgbCommand(
        serialNumber: String,
        command: RadioRgbCommand,
        onSuccess: (() -> Unit)?,
        onError: ((Throwable) -> Unit)?
    ) {
        val topic = RadioDetectionMqttTopics.controlTopic(serialNumber)
        val message = command.toMqttJson().toString()
        val requestAt = System.currentTimeMillis()

        Log.d(
            TAG,
            "RadioDetection RGB send: topic=$topic msgId=${command.msgId} " +
                "mode=${command.mode.wireValue} color=${command.color.wireValue} save=${command.save}"
        )

        ProductMqttRouter.managerFor(ProductType.RadioDetection).publish(
            topic = topic,
            message = message,
            qos = 1,
            queueWhenDisconnected = false,
            onSuccess = {
                Log.d(
                    TAG,
                    "RadioDetection RGB publish succeeded: msgId=${command.msgId} " +
                        "cost=${System.currentTimeMillis() - requestAt}ms"
                )
                onSuccess?.invoke()
            },
            onError = { throwable ->
                Log.e(TAG, "RadioDetection RGB publish failed: msgId=${command.msgId}", throwable)
                onError?.invoke(throwable)
            }
        )
    }

    private companion object {
        const val TAG = "RadioDetectionControlRepo"
    }
}
