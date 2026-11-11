package org.happycode.karoo.lupinepiko.adapters.ble

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LupineModel

class LupinePacketCodecTest : ShouldSpec({

    context("LupinePacketCodec command encoding") {
        should("encode status poll and virtual button commands matching Peak.dll protocol") {
            // then
            LupinePacketCodec.encodeStatusPoll() shouldBe byteArrayOf(0x74, 0x00)
            LupinePacketCodec.encodeUpSideSwitch() shouldBe byteArrayOf(0x74, 0x11)
            LupinePacketCodec.encodeUpSideSwitchOff() shouldBe byteArrayOf(0x74, 0x31)
            LupinePacketCodec.encodeUpSideStepUp() shouldBe byteArrayOf(0x74, 0x41)
            LupinePacketCodec.encodeUpSideStepDown() shouldBe byteArrayOf(0x74, 0x42)
            LupinePacketCodec.encodeDownSideSwitch() shouldBe byteArrayOf(0x74, 0x12)
            LupinePacketCodec.encodeDownSideSwitchOff() shouldBe byteArrayOf(0x74, 0x32)
        }

        should("encode 5-byte direct mode payload for Piko R 2019") {
            // when
            val maxBytes = LupinePacketCodec.encodeDirectMode(
                mode = LightMode.MAX,
                softwareRevision = 4,
                model = LupineModel.PIKO_R_2019,
            )
            val dimmedBytes = LupinePacketCodec.encodeDirectMode(
                mode = LightMode.DIMMED,
                softwareRevision = 4,
                model = LupineModel.PIKO_R_2019,
            )
            val offBytes = LupinePacketCodec.encodeDirectMode(
                mode = LightMode.OFF,
                softwareRevision = 4,
                model = LupineModel.PIKO_R_2019,
            )

            // then
            maxBytes shouldBe byteArrayOf(0x6D, 0x26, 0x04, 0xE8.toByte(), 0x41)
            dimmedBytes shouldBe byteArrayOf(0x6D, 0x26, 0x04, 0x03, 0x41)
            offBytes shouldBe byteArrayOf(0x74, 0x31)
        }

        should("encode 6-byte direct mode payload for classic Piko Blika and Alpha") {
            // when
            val classicBytes = LupinePacketCodec.encodeDirectMode(
                mode = LightMode.DIMMED,
                softwareRevision = 2,
                model = LupineModel.PIKO_CLASSIC,
            )
            val blika2Bytes = LupinePacketCodec.encodeDirectMode(
                mode = LightMode.HIGH,
                softwareRevision = 3,
                model = LupineModel.BLIKA_2,
            )
            val alphaBytes = LupinePacketCodec.encodeDirectMode(
                mode = LightMode.MAX,
                softwareRevision = 1,
                model = LupineModel.ALPHA,
            )

            // then
            classicBytes shouldBe byteArrayOf(0x6D, 0x50, 0x02, 15, 0x41, 0x00)
            blika2Bytes shouldBe byteArrayOf(0x6D, 0x51, 0x03, 0x90.toByte(), 0x41, 0x00)
            alphaBytes shouldBe byteArrayOf(0x6D, 0x60, 0x00, 0xE8.toByte(), 0x01, 0x00)
        }
    }

    context("LupinePacketCodec 16-byte status decoding") {
        should("decode 16-byte status packet from Piko R 2019") {
            // given
            val packet = byteArrayOf(
                0x74, // [0] TYPE 't'
                0x26, // [1] HwId: High=2 (Piko), Low=6 (Piko R 2019)
                0x05, // [2] SwId = 5
                0x20, // [3] LAMP_FLAG = FL_13 (on)
                0x04, // [4] LED_FLAG = B_BLU_ON
                100, // [5] AH_CNT_H -> 1.30 Ah
                0, // [6] AH_CNT_L
                90, // [7] AD_U_AKKU -> 90 * 0.09 = 8.10 V
                116, // [8] AD_I_LED -> (116 / 232.0) * 3.0 = 1.50 A
                0, // [9] reserved
                125, // [10] AD_TEMP -> 20.0 °C
                0, // [11] SOS_TYPE
                1, // [12] M_KEY_FNCT
                232.toByte(), // [13] M_KEY_I -> 3.0 A
                0, // [14] BYTE_14
                0, // [15] BYTE_15
            )

            // when
            val telemetry = LupinePacketCodec.decodeStatus(packet)

            // then
            telemetry shouldNotBe null
            telemetry?.let { t ->
                t.model shouldBe LupineModel.PIKO_R_2019
                t.hardwareRevision shouldBe 6
                t.softwareRevision shouldBe 5
                t.isLampOff shouldBe false
                t.batteryVoltage shouldBe (8.10 plusOrMinus 0.01)
                t.ledCurrentAmps shouldBe (1.50 plusOrMinus 0.01)
                t.maxCurrentAmps shouldBe (3.00 plusOrMinus 0.01)
                t.consumedAh shouldBe (1.30 plusOrMinus 0.01)
                t.temperatureCelsius shouldBe (20.0 plusOrMinus 0.01)
            }
        }

        should("return null when status packet is not 16 bytes") {
            // then
            LupinePacketCodec.decodeStatus(byteArrayOf(0x74, 0x26)) shouldBe null
        }

        should("decode temperature across piecewise ADC ranges and model nibbles") {
            // then
            LupinePacketCodec.decodeTemperature(154) shouldBe (40.0 plusOrMinus 0.01)
            LupinePacketCodec.decodeTemperature(186) shouldBe (50.0 plusOrMinus 0.01)
            LupinePacketCodec.decodeTemperature(197) shouldBe (60.0 plusOrMinus 0.01)
            LupinePacketCodec.decodeTemperature(209) shouldBe (70.0 plusOrMinus 0.01)
            LupinePacketCodec.decodeTemperature(221) shouldBe (80.0 plusOrMinus 0.01)
            LupinePacketCodec.decodeTemperature(226) shouldBe (90.0 plusOrMinus 0.01)
            LupinePacketCodec.decodeTemperature(230) shouldBe (100.0 plusOrMinus 0.01)
            LupinePacketCodec.decodeTemperature(240) shouldBe -1.0

            LupinePacketCodec.decodeModel(highNibble = 2, lowNibble = 1) shouldBe LupineModel.PIKO_CLASSIC
            LupinePacketCodec.decodeModel(highNibble = 5, lowNibble = 0) shouldBe LupineModel.BLIKA
            LupinePacketCodec.decodeModel(highNibble = 5, lowNibble = 1) shouldBe LupineModel.BLIKA_2
            LupinePacketCodec.decodeModel(highNibble = 6, lowNibble = 0) shouldBe LupineModel.ALPHA
            LupinePacketCodec.decodeModel(highNibble = 9, lowNibble = 0) shouldBe LupineModel.UNKNOWN
        }
    }
})
