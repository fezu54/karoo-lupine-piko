package org.happycode.karoo.lupinepiko.application

import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState

class SetLightModeUseCase(
    private val gateway: LupineBleGateway,
) {
    suspend operator fun invoke(mode: LightMode): Result<LightState> =
        gateway.applyMode(mode)
}
