package com.tji.device.util

import android.content.Context
import com.tji.device.data.model.BoundAccountDevice
import com.tji.device.data.model.ProductType
import com.tji.network.MQTTConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// 验证工具
object userData {
    // 存储 MQTT 配置信息，默认为 MQTTConfig.default()
    var mqttConfig: MQTTConfig = MQTTConfig.default()

    // 用于存储用户 ID，注意此字段是私有的且需要在调用前初始化
    private lateinit var userId: String

    /** 登录下发的账号绑定设备（SN + 名称 + 推断产品线） */
    var boundAccountDevices: List<BoundAccountDevice>? = null
    
    private val _selectedLinkSerial = MutableStateFlow<String?>(null)
    val selectedLinkSerialFlow: StateFlow<String?> = _selectedLinkSerial.asStateFlow()

    /** 已选择的设备 SN（用于订阅与悬浮窗当前设备同步） */
    var selectedLinkSerial: String?
        get() = _selectedLinkSerial.value
        set(value) {
            _selectedLinkSerial.value = value
        }

    private val _preferredProductType = MutableStateFlow(ProductType.FireBucket)
    val preferredProductTypeFlow: StateFlow<ProductType> = _preferredProductType.asStateFlow()

    /** 当前偏好的产品类型（用于首页空态与悬浮窗展示） */
    var preferredProductType: ProductType
        get() = _preferredProductType.value
        set(value) {
            _preferredProductType.value = value
        }

    /** @deprecated 使用 boundAccountDevices 替代 */
    @Deprecated("使用 boundAccountDevices", ReplaceWith("boundAccountDevices?.map { it.serialNumber }"))
    var snList: List<String>? = null
        get() = boundAccountDevices?.map { it.serialNumber }
        set(value) {
            field = value
            boundAccountDevices = value?.map { BoundAccountDevice(it, it) }
        }

    fun getLinksForUser(userId: String): List<String> {
        return snList ?: emptyList()
    }

    fun clearLinksForUser() {
        boundAccountDevices = emptyList()
        selectedLinkSerial = null
        preferredProductType = ProductType.FireBucket
    }

    /**
     * 更新 MQTT 配置信息。
     * 如果传入的 username 或 clientId 为 null，则保持原有值不变。
     * @param username 用于更新 MQTT 配置中的用户名
     * @param clientId 用于更新 MQTT 配置中的客户端 ID
     */
    fun updateMqttConfig(username: String? = null, clientId: String? = null) {
        // 更新 mqttConfig，保持非 null 参数值，null 参数则使用现有值
        mqttConfig = mqttConfig.copy(
            username = username ?: mqttConfig.username,  // 如果 username 为 null，使用现有值
            clientId = clientId ?: mqttConfig.clientId   // 如果 clientId 为 null，使用现有值
        )
    }

    /**
     * 设置用户 ID。
     * @param userId 用户 ID
     */
    fun setUserId(userId: String) {
        // 设置 userId，确保在调用之前初始化该变量
        this.userId = userId
    }

    /**
     * 获取当前的用户 ID。
     * @return 返回已设置的用户 ID
     */
    fun getUserId(): String {
        return userId
    }

    fun getUserIdOrNull(): String? {
        return if (this::userId.isInitialized) userId else null
    }

    fun hasUserId(): Boolean {
        return this::userId.isInitialized
    }
}
