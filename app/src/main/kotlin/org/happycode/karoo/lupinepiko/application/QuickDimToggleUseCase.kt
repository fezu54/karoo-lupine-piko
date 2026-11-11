package org.happycode.karoo.lupinepiko.application

import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState

class QuickDimToggleUseCase(
    private val gateway: LupineBleGateway,
    private val configRepository: LightConfigRepository,
) {
    suspend operator fun invoke(): Result<LightState> {
        val currentState = gateway.state.value
        val config = configRepository.config.value

        val targetMode = when (currentState.currentMode) {
            LightMode.OFF -> config.highBeamMode
            config.dimmedMode -> currentState.previousBrightMode.takeIf { it.isBright } ?: config.highBeamMode
            else -> config.dimmedMode
        }

        return gateway.applyMode(targetMode)
    }
}
