package org.happycode.karoo.lupinepiko.domain

enum class LightMode(
    val powerValue: Byte,
    val ledMask: Byte,
    val watts: Double,
    val isBright: Boolean,
) {
    OFF(powerValue = 0x00, ledMask = 0x00, watts = 0.0, isBright = false),
    DIMMED(powerValue = 0x06, ledMask = 0x42, watts = 0.5, isBright = false),
    DIFFUSE(powerValue = 0x1E, ledMask = 0x42, watts = 2.1, isBright = false),
    LOW(powerValue = 0x12, ledMask = 0x41, watts = 1.5, isBright = false),
    MEDIUM(powerValue = 0x3A, ledMask = 0x41, watts = 5.0, isBright = true),
    HIGH(powerValue = 0x8B.toByte(), ledMask = 0x41, watts = 12.0, isBright = true),
    MAX(powerValue = 0xE8.toByte(), ledMask = 0x41, watts = 20.0, isBright = true),
    ;

    companion object {
        val highBeamPresets: List<LightMode> = listOf(MAX, HIGH, MEDIUM)
        val dimmedPresets: List<LightMode> = listOf(DIMMED, DIFFUSE)
        val cyclableModes: List<LightMode> = listOf(DIMMED, DIFFUSE, LOW, MEDIUM, HIGH, MAX)
        val defaultCycleModes: List<LightMode> = listOf(DIMMED, LOW, MEDIUM, HIGH, MAX)
    }
}
