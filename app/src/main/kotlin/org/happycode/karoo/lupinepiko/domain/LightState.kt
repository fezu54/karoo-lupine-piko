package org.happycode.karoo.lupinepiko.domain

data class LightState(
    val currentMode: LightMode = LightMode.OFF,
    val previousBrightMode: LightMode = LightMode.MAX,
    val isConnected: Boolean = false,
    val telemetry: LightTelemetry? = null,
) {
    val isDimmedForOncoming: Boolean
        get() = currentMode == LightMode.DIMMED || currentMode == LightMode.DIFFUSE
}
