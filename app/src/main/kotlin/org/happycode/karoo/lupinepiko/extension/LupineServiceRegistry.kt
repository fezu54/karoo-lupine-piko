package org.happycode.karoo.lupinepiko.extension

import org.happycode.karoo.lupinepiko.adapters.ble.DefaultLupineBleGateway
import org.happycode.karoo.lupinepiko.adapters.storage.SharedPrefsLightConfigRepository
import org.happycode.karoo.lupinepiko.application.CycleLightModeUseCase
import org.happycode.karoo.lupinepiko.application.LightConfigRepository
import org.happycode.karoo.lupinepiko.application.QuickDimToggleUseCase
import org.happycode.karoo.lupinepiko.application.SetLightModeUseCase
import org.happycode.karoo.lupinepiko.application.TogglePowerUseCase

object LupineServiceRegistry {
    val gateway: DefaultLupineBleGateway = DefaultLupineBleGateway()
    val configRepository: LightConfigRepository = SharedPrefsLightConfigRepository()

    val setLightMode: SetLightModeUseCase = SetLightModeUseCase(gateway)
    val quickDimToggle: QuickDimToggleUseCase = QuickDimToggleUseCase(
        gateway = gateway,
        configRepository = configRepository,
    )
    val cycleLightMode: CycleLightModeUseCase = CycleLightModeUseCase(
        gateway = gateway,
        configRepository = configRepository,
    )
    val togglePower: TogglePowerUseCase = TogglePowerUseCase(
        gateway = gateway,
        configRepository = configRepository,
    )
}
