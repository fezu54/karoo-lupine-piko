package org.happycode.karoo.lupinepiko.application

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.happycode.karoo.lupinepiko.adapters.ble.DefaultLupineBleGateway
import org.happycode.karoo.lupinepiko.adapters.storage.SharedPrefsLightConfigRepository
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState
import org.happycode.karoo.lupinepiko.domain.QuickDimConfig

class StepBrightnessUseCaseTest : ShouldSpec({

    should("step up through brightness modes and clamp at max") {
        // given
        val gateway = DefaultLupineBleGateway(initialState = LightState(currentMode = LightMode.OFF))
        val configRepo = SharedPrefsLightConfigRepository(
            initialConfig = QuickDimConfig(
                cycleModes = listOf(LightMode.DIMMED, LightMode.LOW, LightMode.MEDIUM, LightMode.HIGH, LightMode.MAX),
            ),
        )
        val stepBrightness = StepBrightnessUseCase(gateway = gateway, configRepository = configRepo)

        // when & then
        stepBrightness(BrightnessStepDirection.UP).getOrThrow().currentMode shouldBe LightMode.DIMMED
        stepBrightness(BrightnessStepDirection.UP).getOrThrow().currentMode shouldBe LightMode.LOW
        stepBrightness(BrightnessStepDirection.UP).getOrThrow().currentMode shouldBe LightMode.MEDIUM
        stepBrightness(BrightnessStepDirection.UP).getOrThrow().currentMode shouldBe LightMode.HIGH
        stepBrightness(BrightnessStepDirection.UP).getOrThrow().currentMode shouldBe LightMode.MAX
        stepBrightness(BrightnessStepDirection.UP).getOrThrow().currentMode shouldBe LightMode.MAX
    }

    should("step down through brightness modes and clamp at dimmed") {
        // given
        val gateway = DefaultLupineBleGateway(initialState = LightState(currentMode = LightMode.MAX))
        val configRepo = SharedPrefsLightConfigRepository()
        val stepBrightness = StepBrightnessUseCase(gateway = gateway, configRepository = configRepo)

        // when & then
        stepBrightness(BrightnessStepDirection.DOWN).getOrThrow().currentMode shouldBe LightMode.HIGH
        stepBrightness(BrightnessStepDirection.DOWN).getOrThrow().currentMode shouldBe LightMode.MEDIUM
        stepBrightness(BrightnessStepDirection.DOWN).getOrThrow().currentMode shouldBe LightMode.LOW
        stepBrightness(BrightnessStepDirection.DOWN).getOrThrow().currentMode shouldBe LightMode.DIMMED
        stepBrightness(BrightnessStepDirection.DOWN).getOrThrow().currentMode shouldBe LightMode.DIMMED
    }

    should("keep lamp off when stepping down from off") {
        // given
        val sentPayloads = mutableListOf<ByteArray>()
        val gateway = DefaultLupineBleGateway(
            initialState = LightState(currentMode = LightMode.OFF),
            commandSender = { sentPayloads.add(it) },
        )
        val stepBrightness = StepBrightnessUseCase(
            gateway = gateway,
            configRepository = SharedPrefsLightConfigRepository(),
        )

        // when
        val result = stepBrightness(BrightnessStepDirection.DOWN).getOrThrow()

        // then
        result.currentMode shouldBe LightMode.OFF
        sentPayloads.size shouldBe 0
    }
})
