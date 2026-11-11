package org.happycode.karoo.lupinepiko.application

import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState

class CycleLightModeUseCase(
    private val gateway: LupineBleGateway,
    private val configRepository: LightConfigRepository,
) {
    suspend operator fun invoke(): Result<LightState> {
        val cycleModes = configRepository.config.value.cycleModes.ifEmpty { LightMode.defaultCycleModes }
        val currentMode = gateway.state.value.currentMode
        val currentIndex = cycleModes.indexOf(currentMode)
        val nextMode = when {
            currentIndex < 0 -> cycleModes.first()
            else -> cycleModes[(currentIndex + 1) % cycleModes.size]
        }

        return gateway.applyMode(nextMode)
    }
}
