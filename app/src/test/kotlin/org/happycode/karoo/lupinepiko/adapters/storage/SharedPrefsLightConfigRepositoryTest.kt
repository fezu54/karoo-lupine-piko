package org.happycode.karoo.lupinepiko.adapters.storage

import android.content.Context
import android.content.SharedPreferences
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.happycode.karoo.lupinepiko.domain.BatteryCapacity
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.extension.LupineServiceRegistry

class SharedPrefsLightConfigRepositoryTest : ShouldSpec({

    context("SharedPrefsLightConfigRepository") {
        should("load saved preferences and persist updates when config changes") {
            // given
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            val prefs = mockk<SharedPreferences> {
                every { getString(SharedPrefsLightConfigRepository.KEY_HIGH_BEAM_MODE, null) } returns "HIGH"
                every { getString(SharedPrefsLightConfigRepository.KEY_DIMMED_MODE, null) } returns "DIFFUSE"
                every { getString(SharedPrefsLightConfigRepository.KEY_CYCLE_MODES, null) } returns "DIMMED,HIGH,MAX"
                every { getString(SharedPrefsLightConfigRepository.KEY_BATTERY_CAPACITY, null) } returns "AH_6_9"
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
            repository.config.value.batteryCapacity shouldBe BatteryCapacity.AH_6_9

            // when
            repository.updateHighBeamMode(LightMode.MAX)
            repository.updateDimmedMode(LightMode.DIMMED)
            repository.updateCycleModes(listOf(LightMode.MAX, LightMode.LOW))
            repository.toggleCycleMode(LightMode.DIFFUSE)
            repository.updateBatteryCapacity(BatteryCapacity.AH_5_0)

            // then
            repository.config.value.highBeamMode shouldBe LightMode.MAX
            repository.config.value.dimmedMode shouldBe LightMode.DIMMED
            repository.config.value.cycleModes.shouldContainExactly(
                LightMode.DIFFUSE,
                LightMode.LOW,
                LightMode.MAX,
            )
            repository.config.value.batteryCapacity shouldBe BatteryCapacity.AH_5_0
            verify(atLeast = 5) { editor.putString(any(), any()) }
        }

        should("attach SharedPreferences lazily and reject invalid presets when loaded or updated") {
            // given
            val editor = mockk<SharedPreferences.Editor>(relaxed = true)
            val prefs = mockk<SharedPreferences> {
                every { getString(SharedPrefsLightConfigRepository.KEY_HIGH_BEAM_MODE, null) } returns "MEDIUM"
                every { getString(SharedPrefsLightConfigRepository.KEY_DIMMED_MODE, null) } returns "OFF"
                every { getString(SharedPrefsLightConfigRepository.KEY_CYCLE_MODES, null) } returns "OFF,DIMMED"
                every { getString(SharedPrefsLightConfigRepository.KEY_BATTERY_CAPACITY, null) } returns "AH_2_0"
                every { edit() } returns editor
            }
            val repository = SharedPrefsLightConfigRepository()

            // when
            repository.attachPreferences(prefs)
            repository.updateHighBeamMode(LightMode.DIMMED)
            repository.updateDimmedMode(LightMode.MAX)

            // then
            repository.config.value.highBeamMode shouldBe LightMode.MEDIUM
            repository.config.value.dimmedMode shouldBe LightMode.DIMMED
            repository.config.value.cycleModes.shouldContainExactly(LightMode.DIMMED)
            repository.config.value.batteryCapacity shouldBe BatteryCapacity.AH_2_0
        }

        should("bind application SharedPreferences when LupineServiceRegistry initializes") {
            // given
            val prefs = mockk<SharedPreferences>(relaxed = true) {
                every { getString(any(), null) } returns null
            }
            val appContext = mockk<Context> {
                every {
                    getSharedPreferences(
                        SharedPrefsLightConfigRepository.PREFS_NAME,
                        Context.MODE_PRIVATE,
                    )
                } returns prefs
            }
            val context = mockk<Context> {
                every { applicationContext } returns appContext
            }

            // when
            LupineServiceRegistry.initialize(context)

            // then
            verify(exactly = 1) {
                appContext.getSharedPreferences(
                    SharedPrefsLightConfigRepository.PREFS_NAME,
                    Context.MODE_PRIVATE,
                )
            }
        }
    }
})
