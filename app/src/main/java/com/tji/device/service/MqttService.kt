package com.tji.device.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.tji.device.di.AppContainer
import com.tji.network.MqttManager

class MqttService : Service() {

    override fun onCreate() {
        super.onCreate()
        Log.d("MqttService", "Service onCreate")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MqttService", "Service onDestroy")

        AppContainer.mqttSubscriptionManager.cleanup()
        MqttManager.disconnectAll()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // 不需要绑定服务
    }
}
