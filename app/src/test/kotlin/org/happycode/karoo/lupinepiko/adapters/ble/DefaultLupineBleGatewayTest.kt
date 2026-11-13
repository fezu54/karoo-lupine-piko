package org.happycode.karoo.lupinepiko.adapters.ble

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState
import org.happycode.karoo.lupinepiko.domain.LightTelemetry
import org.happycode.karoo.lupinepiko.domain.LupineModel

class DefaultLupineBleGatewayTest : ShouldSpec({

    context("DefaultLupineBleGateway") {
        should("send encoded bytes and update state for button commands and telemetry") {
            // given
            val sentPayloads = mutableListOf<ByteArray>()
            val gateway = DefaultLupineBleGateway(
                commandSender = { bytes -> sentPayloads.add(bytes) },
            )

            // when
            gateway.applyMode(LightMode.MAX)
            gateway.sendButtonSwitch()
            gateway.sendStepUp()
            gateway.sendStepDown()
            gateway.pollStatus()
            gateway.onConnectionChanged(true)

            // then
            sentPayloads.size shouldBe 5
            sentPayloads[0] shouldBe byteArrayOf(0x6D, 0x26, 0x00, 0xE8.toByte(), 0x41)
            sentPayloads[1] shouldBe byteArrayOf(0x74, 0x11)
            sentPayloads[2] shouldBe byteArrayOf(0x74, 0x41)
            sentPayloads[3] shouldBe byteArrayOf(0x74, 0x42)
            sentPayloads[4] shouldBe byteArrayOf(0x74, 0x00)
            gateway.state.value.isConnected shouldBe true
        }

        should("mark currentMode as OFF when telemetry reports lamp off") {
            // given
            val gateway = DefaultLupineBleGateway()
            gateway.applyMode(LightMode.HIGH)

            // when
            gateway.onTelemetryReceived(
                LightTelemetry(
                    model = LupineModel.PIKO_R_2019,
                    hardwareRevision = 6,
                    softwareRevision = 3,
                    isLampOff = true,
                    batteryVoltage = 8.1,
                    ledCurrentAmps = 0.0,
                    maxCurrentAmps = 0.0,
                    consumedAh = 0.5,
                    temperatureCelsius = 22.0,
                )
            )

            // then
            gateway.state.value.currentMode shouldBe LightMode.OFF
            gateway.state.value.previousBrightMode shouldBe LightMode.HIGH
        }

        should("retain previous bright mode when switching from bright mode to non bright mode") {
            // given
            val gateway = DefaultLupineBleGateway(
                initialState = LightState(
                    currentMode = LightMode.HIGH,
                    previousBrightMode = LightMode.MAX,
                ),
            )

            // when
            gateway.applyMode(LightMode.DIMMED)

            // then
            gateway.state.value.currentMode shouldBe LightMode.DIMMED
            gateway.state.value.previousBrightMode shouldBe LightMode.HIGH
        }

        should("clear command sender only when expected sender matches") {
            // given
            val firstSent = mutableListOf<ByteArray>()
            val secondSent = mutableListOf<ByteArray>()
            val firstSender: suspend (ByteArray) -> Unit = { firstSent.add(it) }
            val secondSender: suspend (ByteArray) -> Unit = { secondSent.add(it) }
            val gateway = DefaultLupineBleGateway()

            gateway.setCommandSender(firstSender)
            gateway.setCommandSender(secondSender)

            // when
            gateway.clearCommandSender(firstSender)
            gateway.pollStatus()

            // then
            firstSent.size shouldBe 0
            secondSent.size shouldBe 1

            // when
            gateway.clearCommandSender(secondSender)
            gateway.pollStatus()

            // then
            secondSent.size shouldBe 1
        }
    }
})
