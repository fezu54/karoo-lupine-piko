package org.happycode.karoo.lupinepiko.ui.main

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import org.happycode.karoo.lupinepiko.R
import org.happycode.karoo.lupinepiko.domain.BatteryCapacity
import org.happycode.karoo.lupinepiko.domain.LightMode

class MainScreenLabelTest : ShouldSpec({

    context("labelResId mappings") {
        should("map every LightMode to its localized string resource when queried") {
            LightMode.OFF.labelResId() shouldBe R.string.mode_off
            LightMode.DIMMED.labelResId() shouldBe R.string.mode_dimmed
            LightMode.DIFFUSE.labelResId() shouldBe R.string.mode_diffuse
            LightMode.LOW.labelResId() shouldBe R.string.mode_low
            LightMode.MEDIUM.labelResId() shouldBe R.string.mode_medium
            LightMode.HIGH.labelResId() shouldBe R.string.mode_high
            LightMode.MAX.labelResId() shouldBe R.string.mode_max
        }

        should("map every BatteryCapacity to its localized string resource when queried") {
            BatteryCapacity.AH_2_0.labelResId() shouldBe R.string.battery_capacity_2_0_ah
            BatteryCapacity.AH_3_5.labelResId() shouldBe R.string.battery_capacity_3_5_ah
            BatteryCapacity.AH_5_0.labelResId() shouldBe R.string.battery_capacity_5_0_ah
            BatteryCapacity.AH_6_9.labelResId() shouldBe R.string.battery_capacity_6_9_ah
        }
    }
})
