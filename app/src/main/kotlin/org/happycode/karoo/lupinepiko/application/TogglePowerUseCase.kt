package org.happycode.karoo.lupinepiko.application

import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState

class TogglePowerUseCase(
    private val gateway: LupineBleGateway,
    private val configRepository: LightConfigRepository,
) {
    suspend operator fun invoke(): Result<LightState> {
        val currentState = gateway.state.value
        val targetMode = when (currentState.currentMode) {
            LightMode.OFF -> currentState.previousBrightMode.takeIf { it != LightMode.OFF }
                ?: configRepository.config.value.highBeamMode
            else -> LightMode.OFF
        }

        return gateway.applyMode(targetMode)
    }
}
