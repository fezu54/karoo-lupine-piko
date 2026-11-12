package org.happycode.karoo.lupinepiko.extension

import java.util.Locale
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState
import org.happycode.karoo.lupinepiko.domain.LightTelemetry
import org.happycode.karoo.lupinepiko.domain.LupineModel

enum class TileLayoutSize {
    COMPACT_1X1,
    WIDE_2X1,
    TALL_1X2,
}

enum class BatteryLevelStatus {
    GOOD,
    MEDIUM,
    LOW,
    UNKNOWN,
}

enum class TileBeamStatus {
    DISCONNECTED,
    OFF,
    HIGH_BEAM,
    DIMMED,
    ACTIVE,
}

data class LupineTileHeader(
    val layoutSize: TileLayoutSize,
    val modeLabel: String,
    val wattsLabel: String,
    val modeWithWattsLabel: String,
    val isConnected: Boolean,
    val beamStatus: TileBeamStatus,
    val batteryVoltageLabel: String?,
    val batteryStatus: BatteryLevelStatus,
)

data class LupineControlTileModel(
    val header: LupineTileHeader,
) {
    companion object {
        fun from(
            state: LightState,
            gridSize: Pair<Int, Int>,
            isPreview: Boolean = false,
        ): LupineControlTileModel {
            val effectiveState = LupineTilePresentation.resolveEffectiveState(state, isPreview)
            return LupineControlTileModel(
                header = LupineTilePresentation.buildHeader(effectiveState, gridSize),
            )
        }
    }
}

data class BrightnessBarSegment(
    val mode: LightMode,
    val shortLabel: String,
    val isFilled: Boolean,
    val isSelected: Boolean,
)

data class LupineBrightnessBarModel(
    val header: LupineTileHeader,
    val segments: List<BrightnessBarSegment>,
    val progressPercent: Int,
) {
    companion object {
        private val barModes = LightMode.defaultCycleModes

        fun from(
            state: LightState,
            gridSize: Pair<Int, Int>,
            isPreview: Boolean = false,
        ): LupineBrightnessBarModel {
            val effectiveState = LupineTilePresentation.resolveEffectiveState(state, isPreview)
            val header = LupineTilePresentation.buildHeader(effectiveState, gridSize)
            val activeIndex = resolveActiveIndex(effectiveState)
            val segments = barModes.mapIndexed { index, mode ->
                BrightnessBarSegment(
                    mode = mode,
                    shortLabel = LupineTilePresentation.formatShortMode(mode),
                    isFilled = activeIndex >= index,
                    isSelected = activeIndex == index,
                )
            }
            val progressPercent = if (activeIndex < 0) {
                0
            } else {
                ((activeIndex + 1) * 100) / barModes.size
            }

            return LupineBrightnessBarModel(
                header = header,
                segments = segments,
                progressPercent = progressPercent,
            )
        }

        private fun resolveActiveIndex(state: LightState): Int = when {
            !state.isConnected -> -1
            state.currentMode == LightMode.OFF -> -1
            state.currentMode == LightMode.DIFFUSE -> barModes.indexOf(LightMode.LOW)
            else -> barModes.indexOf(state.currentMode)
        }
    }
}

object LupineTilePresentation {
    private const val FULL_WIDTH_THRESHOLD = 30
    private const val TALL_HEIGHT_THRESHOLD = 25
    private const val BATTERY_GOOD_VOLTAGE = 7.5
    private const val BATTERY_MEDIUM_VOLTAGE = 6.8

    fun resolveLayoutSize(gridSize: Pair<Int, Int>): TileLayoutSize = when {
        gridSize.second > TALL_HEIGHT_THRESHOLD -> TileLayoutSize.TALL_1X2
        gridSize.first > FULL_WIDTH_THRESHOLD -> TileLayoutSize.WIDE_2X1
        else -> TileLayoutSize.COMPACT_1X1
    }

    fun resolveEffectiveState(state: LightState, isPreview: Boolean): LightState =
        if (isPreview && !state.isConnected && state.telemetry == null) {
            previewState()
        } else {
            state
        }

    fun buildHeader(state: LightState, gridSize: Pair<Int, Int>): LupineTileHeader {
        val mode = state.currentMode
        val shortMode = formatShortMode(mode)
        val watts = formatWatts(mode.watts)
        val voltage = state.telemetry?.batteryVoltage

        return LupineTileHeader(
            layoutSize = resolveLayoutSize(gridSize),
            modeLabel = shortMode,
            wattsLabel = watts,
            modeWithWattsLabel = "$shortMode $watts",
            isConnected = state.isConnected,
            beamStatus = resolveBeamStatus(state),
            batteryVoltageLabel = voltage?.let { String.format(Locale.US, "%.1fV", it) },
            batteryStatus = resolveBatteryStatus(voltage),
        )
    }

    fun formatShortMode(mode: LightMode): String = when (mode) {
        LightMode.OFF -> "OFF"
        LightMode.DIMMED -> "DIM"
        LightMode.DIFFUSE -> "DIF"
        LightMode.LOW -> "LOW"
        LightMode.MEDIUM -> "MED"
        LightMode.HIGH -> "HIGH"
        LightMode.MAX -> "MAX"
    }

    fun formatWatts(watts: Double): String =
        if (watts % 1.0 == 0.0) {
            String.format(Locale.US, "%.0fW", watts)
        } else {
            String.format(Locale.US, "%.1fW", watts)
        }

    private fun resolveBeamStatus(state: LightState): TileBeamStatus = when {
        !state.isConnected -> TileBeamStatus.DISCONNECTED
        state.currentMode == LightMode.OFF -> TileBeamStatus.OFF
        state.currentMode.isBright -> TileBeamStatus.HIGH_BEAM
        state.isDimmedForOncoming -> TileBeamStatus.DIMMED
        else -> TileBeamStatus.ACTIVE
    }

    private fun resolveBatteryStatus(voltage: Double?): BatteryLevelStatus = when {
        voltage == null -> BatteryLevelStatus.UNKNOWN
        voltage >= BATTERY_GOOD_VOLTAGE -> BatteryLevelStatus.GOOD
        voltage >= BATTERY_MEDIUM_VOLTAGE -> BatteryLevelStatus.MEDIUM
        else -> BatteryLevelStatus.LOW
    }

    private fun previewState(): LightState = LightState(
        currentMode = LightMode.MAX,
        previousBrightMode = LightMode.MAX,
        isConnected = true,
        telemetry = LightTelemetry(
            model = LupineModel.PIKO_R_2019,
            hardwareRevision = 1,
            softwareRevision = 1,
            isLampOff = false,
            batteryVoltage = 8.1,
            ledCurrentAmps = 2.0,
            maxCurrentAmps = 2.0,
            consumedAh = 0.2,
            temperatureCelsius = 25.0,
        ),
    )
}
