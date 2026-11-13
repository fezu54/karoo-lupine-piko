package org.happycode.karoo.lupinepiko.domain

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class LightStateTest : ShouldSpec({

    context("LightState and LightMode") {
        should("report isDimmedForOncoming true when mode is DIMMED or DIFFUSE") {
            // given
            val dimmedState = LightState(currentMode = LightMode.DIMMED)
            val diffuseState = LightState(currentMode = LightMode.DIFFUSE)
            val maxState = LightState(currentMode = LightMode.MAX)

            // then
            dimmedState.isDimmedForOncoming shouldBe true
            diffuseState.isDimmedForOncoming shouldBe true
            maxState.isDimmedForOncoming shouldBe false
        }

        should("provide expected default cycle modes and preset groups when accessed") {
            // then
            LightMode.defaultCycleModes.shouldContainExactly(
                LightMode.DIMMED,
                LightMode.LOW,
                LightMode.MEDIUM,
                LightMode.HIGH,
                LightMode.MAX,
            )
            LightMode.highBeamPresets.shouldContainExactly(
                LightMode.MAX,
                LightMode.HIGH,
                LightMode.MEDIUM,
            )
            LightMode.dimmedPresets.shouldContainExactly(
                LightMode.DIMMED,
                LightMode.DIFFUSE,
            )
            LightMode.cyclableModes.shouldContainExactly(
                LightMode.DIMMED,
                LightMode.DIFFUSE,
                LightMode.LOW,
                LightMode.MEDIUM,
                LightMode.HIGH,
                LightMode.MAX,
            )
        }
    }

    context("BatteryCapacity and LightTelemetry") {
        should("compute remaining battery percentage clamped to 0..100 when consumedAh varies") {
            // given
            val telemetry = LightTelemetry(
                model = LupineModel.PIKO_R_2019,
                hardwareRevision = 6,
                softwareRevision = 1,
                isLampOff = false,
                batteryVoltage = 7.8,
                ledCurrentAmps = 1.5,
                maxCurrentAmps = 2.5,
                consumedAh = 1.75,
                temperatureCelsius = 24.0,
            )

            // then
            BatteryCapacity.AH_2_0.remainingPercent(consumedAh = 1.0) shouldBe 50
            BatteryCapacity.AH_3_5.remainingPercent(consumedAh = -0.2) shouldBe 100
            BatteryCapacity.AH_5_0.remainingPercent(consumedAh = 6.0) shouldBe 0
            BatteryCapacity.AH_6_9.remainingPercent(consumedAh = 3.45) shouldBe 50
            telemetry.remainingBatteryPercent(BatteryCapacity.AH_3_5) shouldBe 50
            telemetry.remainingBatteryPercent(BatteryCapacity.AH_6_9) shouldBe 75
        }
    }

    context("QuickDimConfig") {
        should("toggle cycle modes in canonical order while keeping at least one mode when toggled") {
            // given
            val initial = QuickDimConfig()

            // when
            val withDiffuse = initial.withToggledCycleMode(LightMode.DIFFUSE)
            val withoutLow = withDiffuse.withToggledCycleMode(LightMode.LOW)
            val ignoreOff = withoutLow.withToggledCycleMode(LightMode.OFF)
            val singleMode = QuickDimConfig(cycleModes = listOf(LightMode.MAX))
            val keepLastMode = singleMode.withToggledCycleMode(LightMode.MAX)

            // then
            withDiffuse.cycleModes.shouldContainExactly(
                LightMode.DIMMED,
                LightMode.DIFFUSE,
                LightMode.LOW,
                LightMode.MEDIUM,
                LightMode.HIGH,
                LightMode.MAX,
            )
            withoutLow.cycleModes.shouldContainExactly(
                LightMode.DIMMED,
                LightMode.DIFFUSE,
                LightMode.MEDIUM,
                LightMode.HIGH,
                LightMode.MAX,
            )
            ignoreOff shouldBe withoutLow
            keepLastMode.cycleModes.shouldContainExactly(LightMode.MAX)
        }
    }
})
