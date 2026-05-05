package com.tji.device.product.firebucket.control

import com.tji.device.di.ProductFloatingQuickControl
import com.tji.device.product.firebucket.model.ControlMode
import com.tji.device.product.firebucket.model.SwitchControlParms
import com.tji.device.product.firebucket.repository.FireBucketSwitchRepository

class FireBucketFloatingQuickControl(
    private val repository: FireBucketSwitchRepository
) : ProductFloatingQuickControl {

    override suspend fun toggleSwitch(linkSerial: String, switchSerial: String, targetAngle: Int) {
        val parms = SwitchControlParms(
            sn = switchSerial,
            angle = targetAngle,
            speed = 100,
            mode = ControlMode.ABSOLUTE
        )
        repository.setAngle(linkSerial, parms)
    }
}
