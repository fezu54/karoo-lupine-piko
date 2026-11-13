package org.happycode.karoo.lupinepiko.extension

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.OnConnectionStatus
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.happycode.karoo.lupinepiko.BuildConfig
import org.happycode.karoo.lupinepiko.adapters.ble.DefaultLupineBleGateway
import org.happycode.karoo.lupinepiko.adapters.ble.LupineBleManager
import org.happycode.karoo.lupinepiko.adapters.ble.LupinePacketCodec
import org.happycode.karoo.lupinepiko.application.CycleLightModeUseCase
import org.happycode.karoo.lupinepiko.application.QuickDimToggleUseCase
import org.happycode.karoo.lupinepiko.application.TogglePowerUseCase
import org.happycode.karoo.lupinepiko.domain.BleConnectionState
import timber.log.Timber

class LupinePikoExtension(
    private val defaultScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    private val scanSettingsFactory: () -> ScanSettings = {
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    },
    private val bluetoothStateFlowFactory: (Context) -> Flow<Boolean> = { it.bluetoothStateFlow() },
    private val bleManagerFactory: (String, CoroutineScope) -> LupineBleManager = { address, scope ->
        LupineBleManager(address = address, scope = scope)
    },
    private val gateway: DefaultLupineBleGateway = LupineServiceRegistry.gateway,
    private val quickDimToggleUseCase: QuickDimToggleUseCase = LupineServiceRegistry.quickDimToggle,
    private val cycleLightModeUseCase: CycleLightModeUseCase = LupineServiceRegistry.cycleLightMode,
    private val togglePowerUseCase: TogglePowerUseCase = LupineServiceRegistry.togglePower,
) : KarooExtension(extension = EXTENSION_ID, version = BuildConfig.VERSION_NAME) {

    @Volatile
    private var activeConnectionToken: Any? = null

    override val types: List<DataTypeImpl> by lazy {
        listOf(
            LupineGraphicalDataType(
                extension = extension,
                typeId = LupineGraphicalDataType.TYPE_LIGHT_CONTROL,
                gateway = gateway,
                scope = defaultScope,
                renderer = LupineControlRemoteViewsRenderer(),
            ),
            LupineGraphicalDataType(
                extension = extension,
                typeId = LupineGraphicalDataType.TYPE_BRIGHTNESS_BAR,
                gateway = gateway,
                scope = defaultScope,
                renderer = LupineBrightnessBarRemoteViewsRenderer(),
            ),
        )
    }

    override fun onCreate() {
        super.onCreate()
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun startScan(emitter: Emitter<Device>) {
        Timber.tag(TAG).d("startScan() called")
        val job = Job(defaultScope.coroutineContext[Job])
        val scope = CoroutineScope(defaultScope.coroutineContext + job)

        val hasScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasScanPermission) {
            Timber.tag(TAG).e("startScan() failed: Missing BLUETOOTH_SCAN or ACCESS_FINE_LOCATION permission")
            emitter.setCancellable { job.cancel() }
            return
        }

        val callback = object : ScanCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val hasConnectPermission =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                if (!hasConnectPermission) {
                    Timber.tag(TAG).w("onScanResult(): Ignored result due to missing BLUETOOTH_CONNECT permission")
                    return
                }

                val deviceAddress = result.device.address
                val name = result.device.name ?: result.scanRecord?.deviceName
                val hasLupineService = result.scanRecord?.serviceUuids?.any { parcelUuid ->
                    parcelUuid == ParcelUuid(UUID.fromString(LupinePacketCodec.SERVICE_LUPINE_UUID))
                } == true

                val displayName = when {
                    name?.let { isLupineDeviceName(it) } == true -> name
                    hasLupineService -> name ?: DEFAULT_DISPLAY_NAME
                    else -> null
                }

                displayName?.let { matchedName ->
                    Timber.tag(TAG).i("Matched Lupine Piko: address=%s, name=%s", deviceAddress, matchedName)
                    emitter.onNext(
                        Device(
                            extension = extension,
                            uid = "$UID_PREFIX${deviceAddress.uppercase()}",
                            dataTypes = types.map { it.dataTypeId },
                            displayName = matchedName,
                        )
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.tag(TAG).e("onScanFailed() called with error code: %d", errorCode)
            }
        }

        val filters = emptyList<ScanFilter>()
        val settings = scanSettingsFactory()
        val bluetoothManager = getSystemService<BluetoothManager>()
        var activeScanner: BluetoothLeScanner? = null

        fun stopActiveScan() {
            activeScanner?.let { scanner ->
                runCatching { scanner.stopScan(callback) }
                activeScanner = null
            }
        }

        fun startLeScan(scanner: BluetoothLeScanner) {
            Timber.tag(TAG).d("startScan(): Bluetooth adapter is ON, starting LE scan")
            runCatching { scanner.startScan(filters, settings, callback) }
                .onSuccess { activeScanner = scanner }
                .onFailure { e -> Timber.tag(TAG).e(e, "startScan(): Failed to start LE scan") }
        }

        scope.launch {
            bluetoothStateFlowFactory(this@LupinePikoExtension)
                .distinctUntilChanged()
                .collect { isEnabled ->
                    val scanner = bluetoothManager?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
                    when {
                        !isEnabled -> {
                            Timber.tag(TAG).w("startScan(): Bluetooth adapter is disabled, pausing scan")
                            stopActiveScan()
                        }
                        scanner == null -> {
                            Timber.tag(TAG).w("startScan(): Bluetooth enabled reported, but scanner is not yet available")
                        }
                        activeScanner == null -> {
                            startLeScan(scanner)
                        }
                    }
                }
        }

        emitter.setCancellable {
            Timber.tag(TAG).d("startScan(): Scan cancelled / stopped")
            job.cancel()
            val canStop =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            if (canStop) {
                stopActiveScan()
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) {
        Timber.tag(TAG).d("connectDevice() called for uid=%s", uid)
        val address = extractMacAddress(uid) ?: run {
            Timber.tag(TAG).w("connectDevice(): could not extract MAC address from uid=%s", uid)
            emitter.setCancellable {}
            return
        }

        val sessionToken = Any()
        activeConnectionToken = sessionToken

        val bleManager = bleManagerFactory(address, defaultScope)
        val commandSender: suspend (ByteArray) -> Unit = { payload -> bleManager.writeCommand(payload) }
        gateway.setCommandSender(commandSender)

        val flowCollectionJob = defaultScope.launch {
            launch {
                bleManager.connectionState.collect { state ->
                    if (activeConnectionToken === sessionToken) {
                        gateway.onConnectionChanged(state == BleConnectionState.CONNECTED)
                    }
                    emitter.onNext(OnConnectionStatus(state.toConnectionStatus()))
                }
            }
            launch {
                bleManager.notificationsEnabled.collect {
                    gateway.pollStatus()
                }
            }
            launch {
                bleManager.incomingData.collect { bytes ->
                    LupinePacketCodec.decodeStatus(bytes)?.let { telemetry ->
                        gateway.onTelemetryReceived(telemetry)
                    }
                }
            }
        }

        emitter.setCancellable {
            Timber.tag(TAG).d("connectDevice(): cancelled for uid=%s", uid)
            flowCollectionJob.cancel()
            bleManager.stop()
            gateway.clearCommandSender(commandSender)
            if (activeConnectionToken === sessionToken) {
                activeConnectionToken = null
                gateway.onConnectionChanged(false)
            }
        }

        bleManager.start()
    }

    override fun onBonusAction(actionId: String) {
        handleAction(actionId)
    }

    fun handleAction(actionId: String) {
        defaultScope.launch {
            when (actionId) {
                ACTION_TOGGLE_DIM -> quickDimToggleUseCase()
                ACTION_CYCLE_MODE -> cycleLightModeUseCase()
                ACTION_TOGGLE_POWER -> togglePowerUseCase()
                else -> Timber.tag(TAG).w("Unknown actionId: %s", actionId)
            }
        }
    }

    private fun BleConnectionState.toConnectionStatus(): ConnectionStatus = when (this) {
        BleConnectionState.CONNECTED -> ConnectionStatus.CONNECTED
        BleConnectionState.SEARCHING -> ConnectionStatus.SEARCHING
        BleConnectionState.DISCONNECTED -> ConnectionStatus.DISCONNECTED
    }

    companion object {
        const val EXTENSION_ID = "karoo-lupine-piko"
        private const val TAG = "PIKO_SCAN"
        private const val UID_PREFIX = "piko-"
        private const val DEFAULT_DISPLAY_NAME = "Lupine Piko"

        const val ACTION_TOGGLE_DIM = "action_toggle_dim"
        const val ACTION_CYCLE_MODE = "action_cycle_mode"
        const val ACTION_TOGGLE_POWER = "action_toggle_power"

        fun isLupineDeviceName(name: String): Boolean =
            name.contains(other = "Lupine", ignoreCase = true) ||
                name.contains(other = "Piko", ignoreCase = true)

        fun extractMacAddress(uid: String): String? =
            uid.removePrefix(UID_PREFIX).takeIf { it.isNotBlank() && it != uid }
    }
}
