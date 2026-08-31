package com.tji.device.product.firebucket.protocol

import com.tji.device.product.firebucket.model.ControlMode

/** App 与 Link 之间的消防吊桶最小 CoreFrame 协议。 */
@Suppress("TooManyFunctions") // 编解码共用同一组帧头、长度和 CRC 规则，保持在一个协议边界内更清楚。
object FireBucketCoreFrame {
    const val DEVICE_TYPE: Int = 0x04
    const val COMMAND_SET_SERVO: Int = 0x02
    const val COMMAND_STATUS_REPORT: Int = 0x03

    private const val MAGIC_T: Int = 0x54
    private const val MAGIC_J: Int = 0x4A
    private const val VERSION: Int = 0x01
    const val FLAG_ACK: Int = 0x01
    const val FLAG_EVENT: Int = 0x02
    private const val HEADER_BYTES: Int = 10
    private const val CRC_BYTES: Int = 2
    private const val ACK_PAYLOAD_BYTES: Int = 1
    private const val MIN_DEVICE_ID_BYTES: Int = 8
    private const val MAX_DEVICE_ID_BYTES: Int = 16
    private const val MIN_ANGLE_DEGREES: Int = -360
    private const val MAX_ANGLE_DEGREES: Int = 360
    private const val MIN_SPEED: Int = 0
    private const val MAX_SPEED: Int = 100
    private const val ANGLE_SCALE: Int = 10

    fun encodeSetServo(
        deviceId: String,
        mode: ControlMode,
        angleDegrees: Int,
        speed: Int,
        sequence: Int
    ): ByteArray {
        val deviceIdBytes = deviceId.encodeToByteArray()
        require(deviceIdBytes.size in MIN_DEVICE_ID_BYTES..MAX_DEVICE_ID_BYTES) { "吊桶编号必须是 8～16 位" }
        require(deviceId.all(::isIdentifierCharacter)) { "吊桶编号只能包含大写字母和数字" }
        require(angleDegrees in angleRange(mode)) { "吊桶角度超出范围" }
        require(speed in MIN_SPEED..MAX_SPEED) { "吊桶速度必须在 0～100 之间" }
        require(sequence in 1..0xFFFF) { "CoreFrame Seq 必须在 1～65535 之间" }

        val angleTenths = angleDegrees * ANGLE_SCALE
        val payload = ByteArray(1 + deviceIdBytes.size + 1 + 2 + 1)
        var offset = 0
        payload[offset++] = deviceIdBytes.size.toByte()
        deviceIdBytes.copyInto(payload, destinationOffset = offset)
        offset += deviceIdBytes.size
        payload[offset++] = when (mode) {
            ControlMode.ABSOLUTE -> 0x00
            ControlMode.RELATIVE -> 0x01
        }
        payload[offset++] = (angleTenths ushr 8).toByte()
        payload[offset++] = angleTenths.toByte()
        payload[offset] = speed.toByte()

        return encodeFrame(
            deviceType = DEVICE_TYPE,
            command = COMMAND_SET_SERVO,
            flags = 0,
            sequence = sequence,
            payload = payload
        )
    }

    fun requireAcceptedAck(frame: ByteArray, expectedSequence: Int) {
        val decoded = decodeFrame(frame)
        require(decoded.deviceType == DEVICE_TYPE) { "Link ACK 设备类型不匹配" }
        require(decoded.command == COMMAND_SET_SERVO) { "Link ACK 命令不匹配" }
        require(decoded.flags == FLAG_ACK) { "Link 返回的不是 ACK" }
        require(decoded.sequence == expectedSequence) { "Link ACK Seq 不匹配" }
        val payloadLength = decoded.payload.size
        require(payloadLength == ACK_PAYLOAD_BYTES) { "Link ACK Payload 长度错误" }
        require(unsigned(decoded.payload[0]) == 0) {
            "Link 拒绝控制命令，状态码=${unsigned(decoded.payload[0])}"
        }
    }

    fun sequence(frame: ByteArray): Int = decodeFrame(frame).sequence

    fun isAck(frame: ByteArray): Boolean = decodeFrame(frame).flags == FLAG_ACK

    fun decodeStatusReport(frame: ByteArray): FireBucketStatusReport {
        val decoded = decodeFrame(frame)
        require(decoded.deviceType == DEVICE_TYPE) { "吊桶状态设备类型不匹配" }
        require(decoded.command == COMMAND_STATUS_REPORT) { "吊桶状态命令不匹配" }
        require(decoded.flags == FLAG_EVENT) { "吊桶状态不是事件帧" }
        require(decoded.sequence != 0) { "吊桶状态 Seq 不能为空" }
        require(decoded.payload.size in MIN_STATUS_PAYLOAD_BYTES..MAX_STATUS_PAYLOAD_BYTES) {
            "吊桶状态 Payload 长度错误"
        }

        var offset = 0
        val serialLength = unsigned(decoded.payload[offset++])
        require(serialLength in MIN_DEVICE_ID_BYTES..MAX_DEVICE_ID_BYTES) { "吊桶状态编号长度错误" }
        require(offset + serialLength + STATUS_FIELDS_AFTER_SERIAL == decoded.payload.size) {
            "吊桶状态编号长度与 Payload 不匹配"
        }
        val serialNumber = decoded.payload.copyOfRange(offset, offset + serialLength).decodeToString()
        offset += serialLength
        require(serialNumber.all(::isIdentifierCharacter)) { "吊桶状态编号非法" }
        val online = unsigned(decoded.payload[offset++])
        require(online in 0..1) { "吊桶在线状态非法" }
        val currentAngle = readI16(decoded.payload, offset) / 10.0
        offset += 2
        val currentCurrent = readU32(decoded.payload, offset) / 10.0
        offset += 4
        val inputVoltage = readU16(decoded.payload, offset) / 100.0
        offset += 2
        val batteryPercentage = readU16(decoded.payload, offset) / 10.0
        offset += 2
        val servoMinAngle = readI16(decoded.payload, offset) / 10.0
        offset += 2
        val servoMaxAngle = readI16(decoded.payload, offset) / 10.0
        offset += 2
        val uptime = readU32(decoded.payload, offset)
        require(currentAngle in servoMinAngle..servoMaxAngle) { "吊桶当前角度非法" }
        require(currentCurrent in 0.0..100_000.0) { "吊桶电流非法" }
        require(inputVoltage in 0.0..100.0) { "吊桶电压非法" }
        require(batteryPercentage in 0.0..100.0) { "吊桶电量非法" }
        return FireBucketStatusReport(
            serialNumber = serialNumber,
            isOnline = online == 1,
            currentAngle = currentAngle,
            currentCurrent = currentCurrent,
            inputVoltage = inputVoltage,
            batteryPercentage = batteryPercentage,
            servoMinAngle = servoMinAngle,
            servoMaxAngle = servoMaxAngle,
            uptimeSeconds = uptime
        )
    }

    fun frameLengthFromHeader(header: ByteArray): Int {
        require(header.size == HEADER_BYTES) { "CoreFrame 固定头长度错误" }
        require(unsigned(header[0]) == MAGIC_T && unsigned(header[1]) == MAGIC_J) { "CoreFrame 帧头错误" }
        return HEADER_BYTES + readU16(header, 8) + CRC_BYTES
    }

    private fun decodeFrame(frame: ByteArray): DecodedFrame {
        require(frame.size >= HEADER_BYTES + CRC_BYTES) { "CoreFrame 长度不足" }
        require(unsigned(frame[0]) == MAGIC_T && unsigned(frame[1]) == MAGIC_J) { "CoreFrame 帧头错误" }
        require(unsigned(frame[2]) == VERSION) { "CoreFrame 协议版本不支持" }
        val payloadLength = readU16(frame, 8)
        require(frame.size == HEADER_BYTES + payloadLength + CRC_BYTES) { "CoreFrame 帧长度错误" }
        require(readU16(frame, frame.lastIndex - 1) == crc16(frame, frame.size - CRC_BYTES)) {
            "CoreFrame CRC 错误"
        }
        return DecodedFrame(
            deviceType = unsigned(frame[3]),
            command = unsigned(frame[4]),
            flags = unsigned(frame[5]),
            sequence = readU16(frame, 6),
            payload = frame.copyOfRange(HEADER_BYTES, HEADER_BYTES + payloadLength)
        )
    }

    private fun encodeFrame(
        deviceType: Int,
        command: Int,
        flags: Int,
        sequence: Int,
        payload: ByteArray
    ): ByteArray {
        val frameWithoutCrc = ByteArray(HEADER_BYTES + payload.size)
        frameWithoutCrc[0] = MAGIC_T.toByte()
        frameWithoutCrc[1] = MAGIC_J.toByte()
        frameWithoutCrc[2] = VERSION.toByte()
        frameWithoutCrc[3] = deviceType.toByte()
        frameWithoutCrc[4] = command.toByte()
        frameWithoutCrc[5] = flags.toByte()
        writeU16(frameWithoutCrc, 6, sequence)
        writeU16(frameWithoutCrc, 8, payload.size)
        payload.copyInto(frameWithoutCrc, destinationOffset = HEADER_BYTES)

        val checksum = crc16(frameWithoutCrc, frameWithoutCrc.size)
        return frameWithoutCrc + byteArrayOf((checksum ushr 8).toByte(), checksum.toByte())
    }

    private fun angleRange(mode: ControlMode): IntRange = when (mode) {
        ControlMode.ABSOLUTE -> 0..MAX_ANGLE_DEGREES
        ControlMode.RELATIVE -> MIN_ANGLE_DEGREES..MAX_ANGLE_DEGREES
    }

    private fun crc16(bytes: ByteArray, length: Int): Int {
        var crc = 0xFFFF
        repeat(length) { index ->
            crc = crc xor (unsigned(bytes[index]) shl 8)
            repeat(Byte.SIZE_BITS) {
                crc = if ((crc and 0x8000) != 0) {
                    (crc shl 1) xor 0x1021
                } else {
                    crc shl 1
                }
                crc = crc and 0xFFFF
            }
        }
        return crc
    }

    private fun readU16(bytes: ByteArray, offset: Int): Int =
        (unsigned(bytes[offset]) shl 8) or unsigned(bytes[offset + 1])

    private fun readI16(bytes: ByteArray, offset: Int): Int = readU16(bytes, offset).let { value ->
        if (value <= Short.MAX_VALUE) value else value - 0x1_0000
    }

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        (unsigned(bytes[offset]).toLong() shl 24) or
            (unsigned(bytes[offset + 1]).toLong() shl 16) or
            (unsigned(bytes[offset + 2]).toLong() shl 8) or
            unsigned(bytes[offset + 3]).toLong()

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }

    private fun unsigned(value: Byte): Int = value.toInt() and 0xFF

    private fun isIdentifierCharacter(value: Char): Boolean =
        value in '0'..'9' || value in 'A'..'Z'

    private data class DecodedFrame(
        val deviceType: Int,
        val command: Int,
        val flags: Int,
        val sequence: Int,
        val payload: ByteArray
    )

    private const val STATUS_FIELDS_OUTSIDE_SERIAL: Int = 20
    private const val STATUS_FIELDS_AFTER_SERIAL: Int = STATUS_FIELDS_OUTSIDE_SERIAL - 1
    private const val MIN_STATUS_PAYLOAD_BYTES: Int = MIN_DEVICE_ID_BYTES + STATUS_FIELDS_OUTSIDE_SERIAL
    private const val MAX_STATUS_PAYLOAD_BYTES: Int = MAX_DEVICE_ID_BYTES + STATUS_FIELDS_OUTSIDE_SERIAL
}

data class FireBucketStatusReport(
    val serialNumber: String,
    val isOnline: Boolean,
    val currentAngle: Double,
    val currentCurrent: Double,
    val inputVoltage: Double,
    val batteryPercentage: Double,
    val servoMinAngle: Double,
    val servoMaxAngle: Double,
    val uptimeSeconds: Long
)
