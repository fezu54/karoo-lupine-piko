package org.happycode.karoo.lupinepiko.extension

import android.content.Context
import android.content.Intent
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.happycode.karoo.lupinepiko.adapters.ble.DefaultLupineBleGateway
import org.happycode.karoo.lupinepiko.adapters.storage.SharedPrefsLightConfigRepository
import org.happycode.karoo.lupinepiko.application.QuickDimToggleUseCase
import org.happycode.karoo.lupinepiko.application.SetLightModeUseCase
import org.happycode.karoo.lupinepiko.application.StepBrightnessUseCase
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState

@OptIn(ExperimentalCoroutinesApi::class)
class LupineTileActionReceiverTest : ShouldSpec({

    fun createReceiver(initialMode: LightMode): Pair<LupineTileActionReceiver, DefaultLupineBleGateway> {
        val gateway = DefaultLupineBleGateway(initialState = LightState(currentMode = initialMode))
        val configRepo = SharedPrefsLightConfigRepository()
        val receiver = LupineTileActionReceiver(
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            quickDimToggleUseCase = QuickDimToggleUseCase(gateway = gateway, configRepository = configRepo),
            stepBrightnessUseCase = StepBrightnessUseCase(gateway = gateway, configRepository = configRepo),
            setLightModeUseCase = SetLightModeUseCase(gateway),
        )
        return receiver to gateway
    }

    should("toggle quick dim when quick dim broadcast action is received") {
        // given
        val (receiver, gateway) = createReceiver(LightMode.MAX)
        val intent = mockk<Intent> {
            every { action } returns LupineTileActionReceiver.ACTION_QUICK_DIM_TOGGLE
            every { getStringExtra(LupineTileActionReceiver.EXTRA_LIGHT_MODE) } returns null
        }

        // when
        receiver.onReceive(mockk<Context>(relaxed = true), intent)

        // then
        gateway.state.value.currentMode shouldBe LightMode.DIMMED
    }

    should("step brightness up and down when stepper actions are received") {
        // given
        val (receiver, gateway) = createReceiver(LightMode.LOW)
        val stepUpIntent = mockk<Intent> {
            every { action } returns LupineTileActionReceiver.ACTION_STEP_BRIGHTNESS_UP
            every { getStringExtra(LupineTileActionReceiver.EXTRA_LIGHT_MODE) } returns null
        }
        val stepDownIntent = mockk<Intent> {
            every { action } returns LupineTileActionReceiver.ACTION_STEP_BRIGHTNESS_DOWN
            every { getStringExtra(LupineTileActionReceiver.EXTRA_LIGHT_MODE) } returns null
        }

        // when
        receiver.onReceive(mockk(relaxed = true), stepUpIntent)
        val afterUp = gateway.state.value.currentMode
        receiver.onReceive(mockk(relaxed = true), stepDownIntent)
        val afterDown = gateway.state.value.currentMode

        // then
        afterUp shouldBe LightMode.MEDIUM
        afterDown shouldBe LightMode.LOW
    }

    should("set explicit light mode when set mode action is received") {
        // given
        val (receiver, gateway) = createReceiver(LightMode.DIMMED)
        val intent = mockk<Intent> {
            every { action } returns LupineTileActionReceiver.ACTION_SET_LIGHT_MODE
            every { getStringExtra(LupineTileActionReceiver.EXTRA_LIGHT_MODE) } returns LightMode.HIGH.name
        }

        // when
        receiver.onReceive(mockk(relaxed = true), intent)

        // then
        gateway.state.value.currentMode shouldBe LightMode.HIGH
    }

    should("ignore unrelated broadcast actions") {
        // given
        val (receiver, gateway) = createReceiver(LightMode.MAX)
        val intent = mockk<Intent> {
            every { action } returns "com.example.UNKNOWN_ACTION"
            every { getStringExtra(any()) } returns null
        }

        // when
        receiver.onReceive(mockk(relaxed = true), intent)

        // then
        gateway.state.value.currentMode shouldBe LightMode.MAX
    }
})
