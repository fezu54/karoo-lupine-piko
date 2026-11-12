package org.happycode.karoo.lupinepiko.application

import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState

enum class BrightnessStepDirection {
    UP,
    DOWN,
}

class StepBrightnessUseCase(
    private val gateway: LupineBleGateway,
    private val configRepository: LightConfigRepository,
) {
    suspend operator fun invoke(direction: BrightnessStepDirection): Result<LightState> {
        val currentState = gateway.state.value
        val modes = configRepository.config.value.cycleModes.ifEmpty { LightMode.defaultCycleModes }
        val targetMode = resolveTargetMode(
            currentMode = currentState.currentMode,
            modes = modes,
            direction = direction,
        ) ?: return Result.success(currentState)

        return gateway.applyMode(targetMode)
    }

    private fun resolveTargetMode(
        currentMode: LightMode,
        modes: List<LightMode>,
        direction: BrightnessStepDirection,
    ): LightMode? {
        val index = modes.indexOf(currentMode)
        return when (direction) {
            BrightnessStepDirection.UP -> when {
                currentMode == LightMode.OFF || index < 0 -> modes.first()
                else -> modes[(index + 1).coerceAtMost(modes.lastIndex)]
            }
            BrightnessStepDirection.DOWN -> when {
                currentMode == LightMode.OFF -> null
                index < 0 -> modes.first()
                else -> modes[(index - 1).coerceAtLeast(0)]
            }
        }
    }
}
