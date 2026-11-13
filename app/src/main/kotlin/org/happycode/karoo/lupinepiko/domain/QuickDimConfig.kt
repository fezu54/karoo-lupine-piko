package org.happycode.karoo.lupinepiko.domain

data class QuickDimConfig(
    val highBeamMode: LightMode = LightMode.MAX,
    val dimmedMode: LightMode = LightMode.DIMMED,
    val cycleModes: List<LightMode> = LightMode.defaultCycleModes,
    val batteryCapacity: BatteryCapacity = BatteryCapacity.DEFAULT,
) {
    fun withToggledCycleMode(mode: LightMode): QuickDimConfig = when {
        mode == LightMode.OFF -> this
        mode in cycleModes -> (cycleModes - mode)
            .takeIf { it.isNotEmpty() }
            ?.let { copy(cycleModes = it) }
            ?: this
        else -> copy(
            cycleModes = LightMode.cyclableModes.filter { it in cycleModes || it == mode },
        )
    }
}
