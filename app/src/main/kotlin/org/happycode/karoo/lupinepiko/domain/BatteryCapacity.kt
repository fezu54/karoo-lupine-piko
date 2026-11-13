package org.happycode.karoo.lupinepiko.domain

import kotlin.math.roundToInt

enum class BatteryCapacity(val ampHours: Double) {
    AH_2_0(ampHours = 2.0),
    AH_3_5(ampHours = 3.5),
    AH_5_0(ampHours = 5.0),
    AH_6_9(ampHours = 6.9),
    ;

    fun remainingPercent(consumedAh: Double): Int = when {
        ampHours <= 0.0 -> 0
        else -> (((ampHours - consumedAh.coerceAtLeast(0.0)) / ampHours) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    companion object {
        val DEFAULT: BatteryCapacity = AH_3_5
    }
}
