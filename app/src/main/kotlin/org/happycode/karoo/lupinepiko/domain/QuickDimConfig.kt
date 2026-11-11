package org.happycode.karoo.lupinepiko.domain

data class QuickDimConfig(
    val highBeamMode: LightMode = LightMode.MAX,
    val dimmedMode: LightMode = LightMode.DIMMED,
    val cycleModes: List<LightMode> = LightMode.defaultCycleModes,
)
