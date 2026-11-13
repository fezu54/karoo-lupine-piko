package org.happycode.karoo.lupinepiko.domain

data class LightTelemetry(
    val model: LupineModel,
    val hardwareRevision: Int,
    val softwareRevision: Int,
    val isLampOff: Boolean,
    val batteryVoltage: Double,
    val ledCurrentAmps: Double,
    val maxCurrentAmps: Double,
    val consumedAh: Double,
    val temperatureCelsius: Double,
) {
    fun remainingBatteryPercent(capacity: BatteryCapacity = BatteryCapacity.DEFAULT): Int =
        capacity.remainingPercent(consumedAh)
}
