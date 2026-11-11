package org.happycode.karoo.lupinepiko.domain

enum class LightMode(
    val powerValue: Byte,
    val ledMask: Byte,
    val watts: Double,
    val isBright: Boolean,
) {
    OFF(powerValue = 0x00, ledMask = 0x00, watts = 0.0, isBright = false),
    DIMMED(powerValue = 0x03, ledMask = 0x41, watts = 0.3, isBright = false),
    DIFFUSE(powerValue = 0x1E, ledMask = 0x42, watts = 2.1, isBright = false),
    LOW(powerValue = 0x18, ledMask = 0x41, watts = 2.0, isBright = false),
    MEDIUM(powerValue = 0x60, ledMask = 0x41, watts = 8.5, isBright = true),
    HIGH(powerValue = 0x90.toByte(), ledMask = 0x41, watts = 13.0, isBright = true),
    MAX(powerValue = 0xE8.toByte(), ledMask = 0x41, watts = 22.0, isBright = true),
    ;

    companion object {
        val defaultCycleModes: List<LightMode> = listOf(DIMMED, LOW, MEDIUM, HIGH, MAX)
    }
}
