package com.tji.device.product.ota

import android.content.Context
import com.tji.device.data.session.DeviceKey

data class SavedOtaTask(
    val taskId: String,
    val cmdId: String
)

interface ProductOtaTaskStore {
    fun get(deviceKey: DeviceKey): SavedOtaTask?
    fun save(deviceKey: DeviceKey, task: SavedOtaTask)
    fun clear(deviceKey: DeviceKey)
}

class InMemoryProductOtaTaskStore : ProductOtaTaskStore {
    private val tasks = mutableMapOf<DeviceKey, SavedOtaTask>()

    override fun get(deviceKey: DeviceKey): SavedOtaTask? = tasks[deviceKey]

    override fun save(deviceKey: DeviceKey, task: SavedOtaTask) {
        tasks[deviceKey] = task
    }

    override fun clear(deviceKey: DeviceKey) {
        tasks.remove(deviceKey)
    }
}

class SharedPreferencesProductOtaTaskStore(context: Context) : ProductOtaTaskStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "product_ota_tasks",
        Context.MODE_PRIVATE
    )

    override fun get(deviceKey: DeviceKey): SavedOtaTask? {
        val key = key(deviceKey)
        val taskId = preferences.getString("$key.taskId", null)?.takeIf { it.isNotBlank() }
        val cmdId = preferences.getString("$key.cmdId", null)?.takeIf { it.isNotBlank() }
        return if (taskId != null && cmdId != null) SavedOtaTask(taskId, cmdId) else null
    }

    override fun save(deviceKey: DeviceKey, task: SavedOtaTask) {
        val key = key(deviceKey)
        check(
            preferences.edit()
                .putString("$key.taskId", task.taskId)
                .putString("$key.cmdId", task.cmdId)
                .commit()
        ) { "升级任务身份保存失败" }
    }

    override fun clear(deviceKey: DeviceKey) {
        val key = key(deviceKey)
        check(
            preferences.edit()
                .remove("$key.taskId")
                .remove("$key.cmdId")
                .commit()
        ) { "升级任务身份清理失败" }
    }

    private fun key(deviceKey: DeviceKey): String =
        "${deviceKey.productType.name}|${deviceKey.serialNumber}"
}
