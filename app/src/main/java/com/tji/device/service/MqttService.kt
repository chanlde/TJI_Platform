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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // MQTT 是账号会话级资源，不跟随 Activity 重建；进程重启后应重新建立登录会话。
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // 不需要绑定服务
    }
}
