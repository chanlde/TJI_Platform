package com.tji.device.product.radiodetection.map

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RadioMapCoordinate(
    val latitude: Double,
    val longitude: Double
)

object RadioCoordinateTransform {
    private const val A = 6378245.0
    private const val EE = 0.00669342162296594323
    private const val PI = Math.PI

    fun wgs84ToGcj02(latitude: Double, longitude: Double): RadioMapCoordinate {
        if (!isUsable(latitude, longitude) || outOfChina(latitude, longitude)) {
            return RadioMapCoordinate(latitude, longitude)
        }

        var dLat = transformLat(longitude - 105.0, latitude - 35.0)
        var dLon = transformLon(longitude - 105.0, latitude - 35.0)
        val radLat = latitude / 180.0 * PI
        var magic = sin(radLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI)
        dLon = (dLon * 180.0) / (A / sqrtMagic * cos(radLat) * PI)
        return RadioMapCoordinate(
            latitude = latitude + dLat,
            longitude = longitude + dLon
        )
    }

    fun isUsable(latitude: Double, longitude: Double): Boolean =
        abs(latitude) > 0.000001 || abs(longitude) > 0.000001

    private fun outOfChina(latitude: Double, longitude: Double): Boolean =
        longitude < 72.004 || longitude > 137.8347 || latitude < 0.8293 || latitude > 55.8271

    private fun transformLat(x: Double, y: Double): Double {
        var ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(y * PI) + 40.0 * sin(y / 3.0 * PI)) * 2.0 / 3.0
        ret += (160.0 * sin(y / 12.0 * PI) + 320 * sin(y * PI / 30.0)) * 2.0 / 3.0
        return ret
    }

    private fun transformLon(x: Double, y: Double): Double {
        var ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        ret += (20.0 * sin(6.0 * x * PI) + 20.0 * sin(2.0 * x * PI)) * 2.0 / 3.0
        ret += (20.0 * sin(x * PI) + 40.0 * sin(x / 3.0 * PI)) * 2.0 / 3.0
        ret += (150.0 * sin(x / 12.0 * PI) + 300.0 * sin(x / 30.0 * PI)) * 2.0 / 3.0
        return ret
    }
}
