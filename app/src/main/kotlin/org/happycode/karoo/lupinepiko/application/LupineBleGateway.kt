package org.happycode.karoo.lupinepiko.application

import kotlinx.coroutines.flow.StateFlow
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState
import org.happycode.karoo.lupinepiko.domain.LightTelemetry

interface LupineBleGateway {
    val state: StateFlow<LightState>

    suspend fun applyMode(mode: LightMode): Result<LightState>

    suspend fun sendButtonSwitch(): Result<Unit>

    suspend fun sendStepUp(): Result<Unit>

    suspend fun sendStepDown(): Result<Unit>

    suspend fun pollStatus(): Result<Unit>

    fun onConnectionChanged(isConnected: Boolean)

    fun onTelemetryReceived(telemetry: LightTelemetry)
}
