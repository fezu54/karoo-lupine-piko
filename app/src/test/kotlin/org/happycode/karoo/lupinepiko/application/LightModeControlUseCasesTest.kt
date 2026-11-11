package org.happycode.karoo.lupinepiko.application

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.happycode.karoo.lupinepiko.adapters.ble.DefaultLupineBleGateway
import org.happycode.karoo.lupinepiko.adapters.storage.SharedPrefsLightConfigRepository
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState
import org.happycode.karoo.lupinepiko.domain.QuickDimConfig

class LightModeControlUseCasesTest : ShouldSpec({

    context("QuickDimToggleUseCase") {
        should("switch immediately from bright mode to dimmed mode and remember previous bright mode") {
            // given
            val gateway = DefaultLupineBleGateway(
                initialState = LightState(
                    currentMode = LightMode.HIGH,
                    previousBrightMode = LightMode.MAX,
                ),
            )
            val configRepo = SharedPrefsLightConfigRepository()
            val quickDimToggle = QuickDimToggleUseCase(gateway = gateway, configRepository = configRepo)

            // when
            val result = quickDimToggle().getOrThrow()

            // then
            result.currentMode shouldBe LightMode.DIMMED
            result.previousBrightMode shouldBe LightMode.HIGH
        }

        should("restore previous bright mode when toggling while already dimmed") {
            // given
            val gateway = DefaultLupineBleGateway(
                initialState = LightState(
                    currentMode = LightMode.DIMMED,
                    previousBrightMode = LightMode.MEDIUM,
                ),
            )
            val configRepo = SharedPrefsLightConfigRepository()
            val quickDimToggle = QuickDimToggleUseCase(gateway = gateway, configRepository = configRepo)

            // when
            val result = quickDimToggle().getOrThrow()

            // then
            result.currentMode shouldBe LightMode.MEDIUM
            result.previousBrightMode shouldBe LightMode.MEDIUM
        }

        should("turn on to configured high beam mode when toggling from OFF") {
            // given
            val gateway = DefaultLupineBleGateway(
                initialState = LightState(currentMode = LightMode.OFF),
            )
            val configRepo = SharedPrefsLightConfigRepository(
                initialConfig = QuickDimConfig(highBeamMode = LightMode.MAX),
            )
            val quickDimToggle = QuickDimToggleUseCase(gateway = gateway, configRepository = configRepo)

            // when
            val result = quickDimToggle().getOrThrow()

            // then
            result.currentMode shouldBe LightMode.MAX
        }
    }

    context("CycleLightModeUseCase") {
        should("cycle sequentially through configured modes and wrap around") {
            // given
            val gateway = DefaultLupineBleGateway(initialState = LightState(currentMode = LightMode.OFF))
            val configRepo = SharedPrefsLightConfigRepository(
                initialConfig = QuickDimConfig(
                    cycleModes = listOf(LightMode.DIMMED, LightMode.MEDIUM, LightMode.MAX),
                ),
            )
            val cycleMode = CycleLightModeUseCase(gateway = gateway, configRepository = configRepo)

            // when & then
            cycleMode().getOrThrow().currentMode shouldBe LightMode.DIMMED
            cycleMode().getOrThrow().currentMode shouldBe LightMode.MEDIUM
            cycleMode().getOrThrow().currentMode shouldBe LightMode.MAX
            cycleMode().getOrThrow().currentMode shouldBe LightMode.DIMMED
        }
    }

    context("SetLightModeUseCase and TogglePowerUseCase") {
        should("set explicit light mode and toggle power off and back to previous bright mode") {
            // given
            val gateway = DefaultLupineBleGateway()
            val configRepo = SharedPrefsLightConfigRepository()
            val setMode = SetLightModeUseCase(gateway)
            val togglePower = TogglePowerUseCase(gateway = gateway, configRepository = configRepo)

            // when
            val afterSet = setMode(LightMode.HIGH).getOrThrow()
            val afterOff = togglePower().getOrThrow()
            val afterRestore = togglePower().getOrThrow()

            // then
            afterSet.currentMode shouldBe LightMode.HIGH
            afterOff.currentMode shouldBe LightMode.OFF
            afterRestore.currentMode shouldBe LightMode.HIGH
        }
    }
})
