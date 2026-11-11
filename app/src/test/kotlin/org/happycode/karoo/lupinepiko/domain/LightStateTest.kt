package org.happycode.karoo.lupinepiko.domain

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class LightStateTest : ShouldSpec({

    context("LightState and LightMode") {
        should("report isDimmedForOncoming true when mode is DIMMED or DIFFUSE") {
            // given
            val dimmedState = LightState(currentMode = LightMode.DIMMED)
            val diffuseState = LightState(currentMode = LightMode.DIFFUSE)
            val maxState = LightState(currentMode = LightMode.MAX)

            // then
            dimmedState.isDimmedForOncoming shouldBe true
            diffuseState.isDimmedForOncoming shouldBe true
            maxState.isDimmedForOncoming shouldBe false
        }

        should("provide expected default cycle modes") {
            // then
            LightMode.defaultCycleModes.shouldContainExactly(
                LightMode.DIMMED,
                LightMode.LOW,
                LightMode.MEDIUM,
                LightMode.HIGH,
                LightMode.MAX,
            )
        }
    }
})
