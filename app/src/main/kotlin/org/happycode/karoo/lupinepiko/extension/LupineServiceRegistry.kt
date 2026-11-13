package org.happycode.karoo.lupinepiko.extension

import android.content.Context
import org.happycode.karoo.lupinepiko.adapters.ble.DefaultLupineBleGateway
import org.happycode.karoo.lupinepiko.adapters.storage.SharedPrefsLightConfigRepository
import org.happycode.karoo.lupinepiko.application.CycleLightModeUseCase
import org.happycode.karoo.lupinepiko.application.QuickDimToggleUseCase
import org.happycode.karoo.lupinepiko.application.SetLightModeUseCase
import org.happycode.karoo.lupinepiko.application.StepBrightnessUseCase
import org.happycode.karoo.lupinepiko.application.TogglePowerUseCase

object LupineServiceRegistry {
    val gateway: DefaultLupineBleGateway = DefaultLupineBleGateway()
    val configRepository: SharedPrefsLightConfigRepository = SharedPrefsLightConfigRepository()

    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(
            SharedPrefsLightConfigRepository.PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        configRepository.attachPreferences(prefs)
    }

    val setLightMode: SetLightModeUseCase = SetLightModeUseCase(gateway)
    val quickDimToggle: QuickDimToggleUseCase = QuickDimToggleUseCase(
        gateway = gateway,
        configRepository = configRepository,
    )
    val cycleLightMode: CycleLightModeUseCase = CycleLightModeUseCase(
        gateway = gateway,
        configRepository = configRepository,
    )
    val stepBrightness: StepBrightnessUseCase = StepBrightnessUseCase(
        gateway = gateway,
        configRepository = configRepository,
    )
    val togglePower: TogglePowerUseCase = TogglePowerUseCase(
        gateway = gateway,
        configRepository = configRepository,
    )
}
