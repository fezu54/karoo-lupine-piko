package org.happycode.karoo.lupinepiko.adapters.ble

import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightTelemetry
import org.happycode.karoo.lupinepiko.domain.LupineModel

object LupinePacketCodec {
    const val SERVICE_LUPINE_UUID = "B29298B2-D595-41C6-5331-4C7570696E65"
    const val CHAR_NOTIFY_COMMAND_UUID = "CB432D3F-5CB9-815B-4342-4C7570696E65"
    const val CHAR_CONFIG_UUID = "CC432D3F-5CB9-815B-4343-4C7570696E65"
    const val CHAR_DATA_STATUS_UUID = "CD432D3F-5CB9-815B-4344-4C7570696E65"

    private const val CMD_STATUS_POLL: Byte = 0x74
    private const val CMD_MODE_ID: Byte = 0x6D
    private const val DEVICE_ID_PIKO_R_2019: Byte = 0x26
    private const val DEVICE_ID_PEAK: Byte = 0x50
    private const val DEVICE_ID_BLIKA_2: Byte = 0x51
    private const val DEVICE_ID_ALPHA: Byte = 0x60

    private const val BUTTON_UPSIDE_SWITCH: Byte = 0x11
    private const val BUTTON_UPSIDE_OFF: Byte = 0x31
    private const val BUTTON_UPSIDE_STEP_UP: Byte = 0x41
    private const val BUTTON_UPSIDE_STEP_DOWN: Byte = 0x42
    private const val BUTTON_DOWNSIDE_SWITCH: Byte = 0x12
    private const val BUTTON_DOWNSIDE_OFF: Byte = 0x32

    private const val STATUS_PACKET_SIZE = 16
    private const val LAMP_FLAG_OFF = 0x80
    private const val CLASSIC_MIN_POWER = 15

    private const val RESOLUTION_AH = 0.013
    private const val RESOLUTION_U_PIKO_R_2019 = 0.09
    private const val RESOLUTION_U_CLASSIC = 0.059
    private const val RESOLUTION_I_DIVISOR = 232.0
    private const val RESOLUTION_I_BASE = 3.0

    fun encodeStatusPoll(): ByteArray = byteArrayOf(CMD_STATUS_POLL, 0x00)

    fun encodeUpSideSwitch(): ByteArray = byteArrayOf(CMD_STATUS_POLL, BUTTON_UPSIDE_SWITCH)

    fun encodeUpSideSwitchOff(): ByteArray = byteArrayOf(CMD_STATUS_POLL, BUTTON_UPSIDE_OFF)

    fun encodeUpSideStepUp(): ByteArray = byteArrayOf(CMD_STATUS_POLL, BUTTON_UPSIDE_STEP_UP)

    fun encodeUpSideStepDown(): ByteArray = byteArrayOf(CMD_STATUS_POLL, BUTTON_UPSIDE_STEP_DOWN)

    fun encodeDownSideSwitch(): ByteArray = byteArrayOf(CMD_STATUS_POLL, BUTTON_DOWNSIDE_SWITCH)

    fun encodeDownSideSwitchOff(): ByteArray = byteArrayOf(CMD_STATUS_POLL, BUTTON_DOWNSIDE_OFF)

    fun encodeDirectMode(
        mode: LightMode,
        softwareRevision: Int = 0,
        model: LupineModel = LupineModel.PIKO_R_2019,
    ): ByteArray = when (mode) {
        LightMode.OFF -> encodeUpSideSwitchOff()
        else -> when (model) {
            LupineModel.PIKO_R_2019 -> byteArrayOf(
                CMD_MODE_ID,
                DEVICE_ID_PIKO_R_2019,
                softwareRevision.toByte(),
                mode.powerValue,
                mode.ledMask,
            )
            LupineModel.BLIKA_2 -> byteArrayOf(
                CMD_MODE_ID,
                DEVICE_ID_BLIKA_2,
                softwareRevision.toByte(),
                (mode.powerValue.toInt() and 0xFF).coerceAtLeast(CLASSIC_MIN_POWER).toByte(),
                mode.ledMask,
                0x00,
            )
            LupineModel.ALPHA -> byteArrayOf(
                CMD_MODE_ID,
                DEVICE_ID_ALPHA,
                0x00,
                mode.powerValue,
                0x01,
                0x00,
            )
            LupineModel.PIKO_CLASSIC,
            LupineModel.BLIKA,
            LupineModel.UNKNOWN,
            -> byteArrayOf(
                CMD_MODE_ID,
                DEVICE_ID_PEAK,
                softwareRevision.toByte(),
                (mode.powerValue.toInt() and 0xFF).coerceAtLeast(CLASSIC_MIN_POWER).toByte(),
                mode.ledMask,
                0x00,
            )
        }
    }

    fun decodeStatus(bytes: ByteArray): LightTelemetry? =
        bytes.takeIf { it.size == STATUS_PACKET_SIZE }?.let { packet ->
            val hwId = packet[1].toInt() and 0xFF
            val highNibble = (hwId ushr 4) and 0x0F
            val lowNibble = hwId and 0x0F
            val model = decodeModel(highNibble = highNibble, lowNibble = lowNibble)
            val swRev = packet[2].toInt() and 0xFF
            val lampFlag = packet[3].toInt() and 0xFF
            val ahCountHigh = packet[5].toInt() and 0xFF
            val adUAkku = packet[7].toInt() and 0xFF
            val adILed = packet[8].toInt() and 0xFF
            val adTemp = packet[10].toInt() and 0xFF
            val mKeyI = packet[13].toInt() and 0xFF

            val voltageFactor = when (model) {
                LupineModel.PIKO_R_2019 -> RESOLUTION_U_PIKO_R_2019
                else -> RESOLUTION_U_CLASSIC
            }

            LightTelemetry(
                model = model,
                hardwareRevision = lowNibble,
                softwareRevision = swRev,
                isLampOff = (lampFlag and LAMP_FLAG_OFF) != 0,
                batteryVoltage = adUAkku * voltageFactor,
                ledCurrentAmps = (adILed / RESOLUTION_I_DIVISOR) * RESOLUTION_I_BASE,
                maxCurrentAmps = (mKeyI / RESOLUTION_I_DIVISOR) * RESOLUTION_I_BASE,
                consumedAh = ahCountHigh * RESOLUTION_AH,
                temperatureCelsius = decodeTemperature(adTemp),
            )
        }

    fun decodeModel(highNibble: Int, lowNibble: Int): LupineModel = when (highNibble) {
        2 -> if (lowNibble == 6) LupineModel.PIKO_R_2019 else LupineModel.PIKO_CLASSIC
        5 -> when (lowNibble) {
            0 -> LupineModel.BLIKA
            1 -> LupineModel.BLIKA_2
            else -> LupineModel.UNKNOWN
        }
        6 -> LupineModel.ALPHA
        else -> LupineModel.UNKNOWN
    }

    fun decodeTemperature(adTemp: Int): Double {
        val raw = adTemp.toDouble()
        return when {
            raw <= 125.0 -> (raw / 125.0) * 20.0
            raw <= 154.0 -> (raw / 154.0) * 40.0
            raw <= 186.0 -> (raw / 186.0) * 50.0
            raw <= 197.0 -> (raw / 197.0) * 60.0
            raw <= 209.0 -> (raw / 209.0) * 70.0
            raw <= 221.0 -> (raw / 221.0) * 80.0
            raw <= 226.0 -> (raw / 226.0) * 90.0
            raw <= 230.0 -> (raw / 230.0) * 100.0
            else -> -1.0
        }
    }
}
