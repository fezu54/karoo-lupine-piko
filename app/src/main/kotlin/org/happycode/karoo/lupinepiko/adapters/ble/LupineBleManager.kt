package org.happycode.karoo.lupinepiko.adapters.ble

import android.Manifest
import androidx.annotation.RequiresPermission
import com.juul.kable.Advertisement
import com.juul.kable.Characteristic
import com.juul.kable.DiscoveredCharacteristic
import com.juul.kable.DiscoveredService
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.WriteType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.happycode.karoo.lupinepiko.domain.BleConnectionState
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

class LupineBleManager(
    private val address: String,
    private val scope: CoroutineScope,
    scanner: Scanner<Advertisement>? = null,
    private val peripheralFactory: (Advertisement) -> Peripheral = { Peripheral(it) },
) {
    private val internalScanner: Scanner<Advertisement> by lazy {
        scanner ?: Scanner {
            filters { match { address = this@LupineBleManager.address } }
        }
    }

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _incomingData = MutableSharedFlow<ByteArray>()
    val incomingData: SharedFlow<ByteArray> = _incomingData.asSharedFlow()

    private val _notificationsEnabled = MutableSharedFlow<Unit>()
    val notificationsEnabled: SharedFlow<Unit> = _notificationsEnabled.asSharedFlow()

    private var connectionJob: Job? = null

    @Volatile
    private var peripheral: Peripheral? = null

    @Volatile
    private var commandCharacteristic: Characteristic? = null

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    fun start() {
        if (connectionJob?.isActive == true) return
        connectionJob = scope.launch {
            connectionFlow()
                .retry { e ->
                    if (e is CancellationException) return@retry false
                    if (e !is ConnectionRestartException) {
                        Timber.tag(TAG).e(e, "Connection loop error for %s", address)
                    }
                    delay(RECONNECT_DELAY)
                    true
                }
                .collect()
        }
    }

    private fun connectionFlow() = flow<Unit> {
        val advertisement = scanForDevice()
        val currentPeripheral = peripheralFactory(advertisement).also { peripheral = it }
        try {
            handleSession(currentPeripheral)
        } finally {
            disposePeripheral(currentPeripheral)
            cleanupSession()
        }
        throw ConnectionRestartException()
    }

    private suspend fun scanForDevice(): Advertisement {
        _connectionState.value = BleConnectionState.SEARCHING
        return internalScanner.advertisements.first()
    }

    private suspend fun handleSession(peripheral: Peripheral) {
        coroutineScope {
            val sessionJob = Job(coroutineContext[Job])
            val sessionScope = CoroutineScope(coroutineContext + sessionJob)
            try {
                peripheral.state
                    .onEach { _connectionState.value = it.toBleConnectionState() }
                    .launchIn(sessionScope)

                peripheral.connect()

                val services = peripheral.services.first { it != null }
                services?.findService(LupinePacketCodec.SERVICE_LUPINE_UUID)?.let { service ->
                    setupLamp(
                        sessionScope = sessionScope,
                        peripheral = peripheral,
                        service = service,
                    )
                }

                peripheral.state.first { it is State.Disconnected }
            } finally {
                sessionJob.cancel()
            }
        }
    }

    private fun setupLamp(
        sessionScope: CoroutineScope,
        peripheral: Peripheral,
        service: DiscoveredService,
    ) {
        commandCharacteristic = service.findCharacteristic(LupinePacketCodec.CHAR_NOTIFY_COMMAND_UUID)

        service.findCharacteristic(LupinePacketCodec.CHAR_DATA_STATUS_UUID)?.let { dataChar ->
            peripheral.observe(
                characteristic = dataChar,
                onSubscription = { _notificationsEnabled.emit(Unit) },
            )
                .onEach { _incomingData.emit(it) }
                .catch { Timber.tag(TAG).e(it, "Notification error") }
                .launchIn(sessionScope)
        }
    }

    private suspend fun disposePeripheral(target: Peripheral) {
        withContext(NonCancellable) {
            try {
                target.disconnect()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Disconnect error")
            } finally {
                try {
                    target.close()
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Close error")
                }
            }
        }
    }

    private fun cleanupSession() {
        commandCharacteristic = null
        peripheral = null
        _connectionState.value = BleConnectionState.DISCONNECTED
    }

    fun stop() {
        connectionJob?.cancel()
        connectionJob = null
        cleanupSession()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun writeCommand(cmdBytes: ByteArray) {
        val char = commandCharacteristic ?: error("Command characteristic not available")
        val activePeripheral = peripheral ?: error("BLE peripheral not connected")
        try {
            activePeripheral.write(char, cmdBytes, WriteType.WithResponse)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error writing command")
            throw e
        }
    }

    private fun State.toBleConnectionState(): BleConnectionState = when (this) {
        is State.Connected -> BleConnectionState.CONNECTED
        is State.Connecting -> BleConnectionState.SEARCHING
        else -> BleConnectionState.DISCONNECTED
    }

    private fun List<DiscoveredService>.findService(uuidString: String): DiscoveredService? =
        find { it.serviceUuid.toString().equals(other = uuidString, ignoreCase = true) }

    private fun DiscoveredService.findCharacteristic(uuidString: String): DiscoveredCharacteristic? =
        characteristics.find { it.characteristicUuid.toString().equals(other = uuidString, ignoreCase = true) }

    private class ConnectionRestartException : Exception()

    companion object {
        private const val TAG = "LUPINE_BLE"
        private val RECONNECT_DELAY = 5.seconds
    }
}
