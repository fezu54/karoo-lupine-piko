package org.happycode.karoo.lupinepiko.extension

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState
import org.happycode.karoo.lupinepiko.domain.LightTelemetry
import org.happycode.karoo.lupinepiko.domain.LupineModel

class LupineTilePresentationTest : ShouldSpec({

    fun telemetryWithVoltage(voltage: Double) = LightTelemetry(
        model = LupineModel.PIKO_R_2019,
        hardwareRevision = 6,
        softwareRevision = 1,
        isLampOff = false,
        batteryVoltage = voltage,
        ledCurrentAmps = 1.5,
        maxCurrentAmps = 2.0,
        consumedAh = 0.4,
        temperatureCelsius = 24.0,
    )

    should("resolve compact wide and tall tile layouts from Karoo grid size") {
        LupineTilePresentation.resolveLayoutSize(30 to 15) shouldBe TileLayoutSize.COMPACT_1X1
        LupineTilePresentation.resolveLayoutSize(60 to 15) shouldBe TileLayoutSize.WIDE_2X1
        LupineTilePresentation.resolveLayoutSize(30 to 30) shouldBe TileLayoutSize.TALL_1X2
        LupineTilePresentation.resolveLayoutSize(60 to 30) shouldBe TileLayoutSize.TALL_1X2
    }

    should("format mode wattage and beam status when connected") {
        // given
        val maxState = LightState(
            currentMode = LightMode.MAX,
            isConnected = true,
            telemetry = telemetryWithVoltage(8.1),
        )
        val dimmedState = LightState(
            currentMode = LightMode.DIMMED,
            isConnected = true,
            telemetry = telemetryWithVoltage(7.1),
        )
        val lowState = LightState(
            currentMode = LightMode.LOW,
            isConnected = true,
            telemetry = telemetryWithVoltage(7.1),
        )

        // when
        val maxModel = LupineControlTileModel.from(state = maxState, gridSize = 60 to 15)
        val dimModel = LupineControlTileModel.from(state = dimmedState, gridSize = 30 to 15)
        val lowModel = LupineControlTileModel.from(state = lowState, gridSize = 30 to 15)

        // then
        maxModel.header.modeWithWattsLabel shouldBe "MAX 20W"
        maxModel.header.batteryVoltageLabel shouldBe "8.1V"
        maxModel.header.batteryStatus shouldBe BatteryLevelStatus.GOOD
        maxModel.header.beamStatus shouldBe TileBeamStatus.HIGH_BEAM

        dimModel.header.modeWithWattsLabel shouldBe "DIM 0.5W"
        dimModel.header.batteryVoltageLabel shouldBe "7.1V"
        dimModel.header.batteryStatus shouldBe BatteryLevelStatus.MEDIUM
        dimModel.header.beamStatus shouldBe TileBeamStatus.DIMMED

        lowModel.header.modeWithWattsLabel shouldBe "LOW 1.5W"
        lowModel.header.beamStatus shouldBe TileBeamStatus.ACTIVE
    }

    should("classify low and unknown battery voltage and provide preview fallback") {
        // given
        val lowBattery = LupineControlTileModel.from(
            state = LightState(currentMode = LightMode.LOW, isConnected = true, telemetry = telemetryWithVoltage(6.4)),
            gridSize = 30 to 15,
        )
        val disconnected = LupineControlTileModel.from(
            state = LightState(),
            gridSize = 30 to 15,
            isPreview = false,
        )
        val preview = LupineControlTileModel.from(
            state = LightState(),
            gridSize = 30 to 15,
            isPreview = true,
        )

        // then
        lowBattery.header.batteryStatus shouldBe BatteryLevelStatus.LOW
        disconnected.header.isConnected shouldBe false
        disconnected.header.batteryStatus shouldBe BatteryLevelStatus.UNKNOWN
        disconnected.header.beamStatus shouldBe TileBeamStatus.DISCONNECTED
        preview.header.isConnected shouldBe true
        preview.header.modeWithWattsLabel shouldBe "MAX 20W"
        preview.header.beamStatus shouldBe TileBeamStatus.HIGH_BEAM
    }

    should("map brightness bar segments and progress percentage for active light modes") {
        // given
        val mediumState = LightState(currentMode = LightMode.MEDIUM, isConnected = true)
        val diffuseState = LightState(currentMode = LightMode.DIFFUSE, isConnected = true)
        val offState = LightState(currentMode = LightMode.OFF, isConnected = true)

        // when
        val mediumModel = LupineBrightnessBarModel.from(state = mediumState, gridSize = 60 to 15)
        val diffuseModel = LupineBrightnessBarModel.from(state = diffuseState, gridSize = 30 to 15)
        val offModel = LupineBrightnessBarModel.from(state = offState, gridSize = 60 to 15)

        // then
        mediumModel.progressPercent shouldBe 60
        mediumModel.header.beamStatus shouldBe TileBeamStatus.HIGH_BEAM
        mediumModel.segments.map { it.isFilled } shouldBe listOf(true, true, true, false, false)
        mediumModel.segments.map { it.isSelected } shouldBe listOf(false, false, true, false, false)

        diffuseModel.progressPercent shouldBe 40
        offModel.progressPercent shouldBe 0
        offModel.header.beamStatus shouldBe TileBeamStatus.OFF
        offModel.segments.any { it.isFilled } shouldBe false
    }
})
