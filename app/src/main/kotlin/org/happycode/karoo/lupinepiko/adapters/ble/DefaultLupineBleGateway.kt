package org.happycode.karoo.lupinepiko.adapters.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.happycode.karoo.lupinepiko.application.LupineBleGateway
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState
import org.happycode.karoo.lupinepiko.domain.LightTelemetry
import org.happycode.karoo.lupinepiko.domain.LupineModel

class DefaultLupineBleGateway(
    initialState: LightState = LightState(),
    private var commandSender: suspend (ByteArray) -> Unit = {},
) : LupineBleGateway {
    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<LightState> = _state.asStateFlow()

    fun setCommandSender(sender: suspend (ByteArray) -> Unit) {
        commandSender = sender
    }

    override suspend fun applyMode(mode: LightMode): Result<LightState> = runCatching {
        val currentTelemetry = _state.value.telemetry
        val payload = LupinePacketCodec.encodeDirectMode(
            mode = mode,
            softwareRevision = currentTelemetry?.softwareRevision ?: 0,
            model = currentTelemetry?.model ?: LupineModel.PIKO_R_2019,
        )
        commandSender(payload)
        _state.update { current ->
            current.copy(
                currentMode = mode,
                previousBrightMode = if (mode.isBright) mode else current.previousBrightMode,
            )
        }
        _state.value
    }

    override suspend fun sendButtonSwitch(): Result<Unit> = runCatching {
        commandSender(LupinePacketCodec.encodeUpSideSwitch())
    }

    override suspend fun sendStepUp(): Result<Unit> = runCatching {
        commandSender(LupinePacketCodec.encodeUpSideStepUp())
    }

    override suspend fun sendStepDown(): Result<Unit> = runCatching {
        commandSender(LupinePacketCodec.encodeUpSideStepDown())
    }

    override suspend fun pollStatus(): Result<Unit> = runCatching {
        commandSender(LupinePacketCodec.encodeStatusPoll())
    }

    override fun onConnectionChanged(isConnected: Boolean) {
        _state.update { current ->
            current.copy(isConnected = isConnected)
        }
    }

    override fun onTelemetryReceived(telemetry: LightTelemetry) {
        _state.update { current ->
            current.copy(
                isConnected = true,
                telemetry = telemetry,
                currentMode = if (telemetry.isLampOff) LightMode.OFF else current.currentMode,
            )
        }
    }
}
