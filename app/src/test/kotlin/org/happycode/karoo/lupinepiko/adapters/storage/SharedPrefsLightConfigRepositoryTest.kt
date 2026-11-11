package org.happycode.karoo.lupinepiko.adapters.storage

import android.content.SharedPreferences
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.happycode.karoo.lupinepiko.domain.LightMode

class SharedPrefsLightConfigRepositoryTest : ShouldSpec({

    context("SharedPrefsLightConfigRepository") {
        should("load saved preferences and persist updates to SharedPreferences") {
            // given
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            val prefs = mockk<SharedPreferences> {
                every { getString(SharedPrefsLightConfigRepository.KEY_HIGH_BEAM_MODE, null) } returns "HIGH"
                every { getString(SharedPrefsLightConfigRepository.KEY_DIMMED_MODE, null) } returns "DIFFUSE"
                every { getString(SharedPrefsLightConfigRepository.KEY_CYCLE_MODES, null) } returns "DIMMED,HIGH,MAX"
                every { edit() } returns editor
            }
            val repository = SharedPrefsLightConfigRepository(preferences = prefs)

            // then
            repository.config.value.highBeamMode shouldBe LightMode.HIGH
            repository.config.value.dimmedMode shouldBe LightMode.DIFFUSE
            repository.config.value.cycleModes.shouldContainExactly(
                LightMode.DIMMED,
                LightMode.HIGH,
                LightMode.MAX,
            )

            // when
            repository.updateHighBeamMode(LightMode.MAX)
            repository.updateDimmedMode(LightMode.DIMMED)
            repository.updateCycleModes(listOf(LightMode.LOW, LightMode.MAX))

            // then
            repository.config.value.highBeamMode shouldBe LightMode.MAX
            repository.config.value.dimmedMode shouldBe LightMode.DIMMED
            repository.config.value.cycleModes.shouldContainExactly(LightMode.LOW, LightMode.MAX)
            verify(atLeast = 3) { editor.putString(any(), any()) }
        }
    }
})
