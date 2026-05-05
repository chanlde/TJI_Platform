package com.tji.device.product.firebucket.repository

import com.tji.device.product.firebucket.model.SwitchControlParms

/**
 * FireBucket 舵机 / 开关设备的数据仓库接口。
 */
interface FireBucketSwitchRepository {
    suspend fun restart(sn: String)
    suspend fun setAngle(linkSn: String, scParms: SwitchControlParms)
}
