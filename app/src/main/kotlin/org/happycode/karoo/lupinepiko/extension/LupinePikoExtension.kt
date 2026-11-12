package org.happycode.karoo.lupinepiko.extension

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.happycode.karoo.lupinepiko.BuildConfig
import org.happycode.karoo.lupinepiko.adapters.ble.DefaultLupineBleGateway
import org.happycode.karoo.lupinepiko.adapters.ble.LupinePacketCodec
import org.happycode.karoo.lupinepiko.application.CycleLightModeUseCase
import org.happycode.karoo.lupinepiko.application.QuickDimToggleUseCase
import org.happycode.karoo.lupinepiko.application.TogglePowerUseCase

class LupinePikoExtension(
    private val defaultScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val scanSettingsFactory: () -> ScanSettings = {
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    },
    private val bluetoothStateFlowFactory: (Context) -> Flow<Boolean> = { it.bluetoothStateFlow() },
    private val gateway: DefaultLupineBleGateway = LupineServiceRegistry.gateway,
    private val quickDimToggleUseCase: QuickDimToggleUseCase = LupineServiceRegistry.quickDimToggle,
    private val cycleLightModeUseCase: CycleLightModeUseCase = LupineServiceRegistry.cycleLightMode,
    private val togglePowerUseCase: TogglePowerUseCase = LupineServiceRegistry.togglePower,
) : KarooExtension(extension = EXTENSION_ID, version = BuildConfig.VERSION_NAME) {

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun startScan(emitter: Emitter<Device>) {
        Log.d(TAG, "startScan() called")
        val job = Job(defaultScope.coroutineContext[Job])
        val scope = CoroutineScope(defaultScope.coroutineContext + job)

        val hasScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasScanPermission) {
            Log.e(TAG, "startScan() failed: Missing BLUETOOTH_SCAN or ACCESS_FINE_LOCATION permission")
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
                    Log.w(TAG, "onScanResult(): Ignored result due to missing BLUETOOTH_CONNECT permission")
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
                    Log.i(TAG, "Matched Lupine Piko: address=$deviceAddress, name=$matchedName")
                    emitter.onNext(
                        Device(
                            extension = extension,
                            uid = "$UID_PREFIX${deviceAddress.uppercase()}",
                            dataTypes = emptyList(),
                            displayName = matchedName,
                        )
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "onScanFailed() called with error code: $errorCode")
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
            Log.d(TAG, "startScan(): Bluetooth adapter is ON, starting LE scan")
            runCatching { scanner.startScan(filters, settings, callback) }
                .onSuccess { activeScanner = scanner }
                .onFailure { e -> Log.e(TAG, "startScan(): Failed to start LE scan", e) }
        }

        scope.launch {
            bluetoothStateFlowFactory(this@LupinePikoExtension)
                .distinctUntilChanged()
                .collect { isEnabled ->
                    val scanner = bluetoothManager?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
                    when {
                        !isEnabled -> {
                            Log.w(TAG, "startScan(): Bluetooth adapter is disabled, pausing scan")
                            stopActiveScan()
                        }
                        scanner == null -> {
                            Log.w(TAG, "startScan(): Bluetooth enabled reported, but scanner is not yet available")
                        }
                        activeScanner == null -> {
                            startLeScan(scanner)
                        }
                    }
                }
        }

        emitter.setCancellable {
            Log.d(TAG, "startScan(): Scan cancelled / stopped")
            job.cancel()
            val canStop =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            if (canStop) {
                stopActiveScan()
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) {
        Log.d(TAG, "connectDevice() called for uid=$uid")
        val address = extractMacAddress(uid)
        val bluetoothDevice = address?.let { mac ->
            getSystemService<BluetoothManager>()?.adapter?.let { adapter ->
                runCatching { adapter.getRemoteDevice(mac) }.getOrNull()
            }
        }

        val gatt = bluetoothDevice?.let { device ->
            emitter.onNext(OnConnectionStatus(ConnectionStatus.SEARCHING))
            connectGattDevice(device, emitter)
        }

        emitter.setCancellable {
            Log.d(TAG, "connectDevice(): cancelled for uid=$uid")
            gateway.onConnectionChanged(false)
            gatt?.let { activeGatt ->
                runCatching {
                    activeGatt.disconnect()
                    activeGatt.close()
                }
            }
        }
    }

    fun handleAction(actionId: String) {
        defaultScope.launch {
            when (actionId) {
                ACTION_TOGGLE_DIM -> quickDimToggleUseCase()
                ACTION_CYCLE_MODE -> cycleLightModeUseCase()
                ACTION_TOGGLE_POWER -> togglePowerUseCase()
                else -> Log.w(TAG, "Unknown actionId: $actionId")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectGattDevice(
        device: BluetoothDevice,
        emitter: Emitter<DeviceEvent>,
    ): BluetoothGatt? {
        val serviceUuid = UUID.fromString(LupinePacketCodec.SERVICE_LUPINE_UUID)
        val commandUuid = UUID.fromString(LupinePacketCodec.CHAR_NOTIFY_COMMAND_UUID)
        val dataUuid = UUID.fromString(LupinePacketCodec.CHAR_DATA_STATUS_UUID)
        val cccdUuid = UUID.fromString(CCCD_UUID)

        val gattCallback = object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        gateway.onConnectionChanged(true)
                        emitter.onNext(OnConnectionStatus(ConnectionStatus.CONNECTED))
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        gateway.onConnectionChanged(false)
                        emitter.onNext(OnConnectionStatus(ConnectionStatus.DISCONNECTED))
                    }
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) return
                val service = gatt.getService(serviceUuid) ?: return

                service.getCharacteristic(commandUuid)?.let { commandChar ->
                    gateway.setCommandSender { payload ->
                        writeCharacteristicBytes(gatt, commandChar, payload)
                    }
                }

                service.getCharacteristic(dataUuid)?.let { dataChar ->
                    gatt.setCharacteristicNotification(dataChar, true)
                    dataChar.getDescriptor(cccdUuid)?.let { descriptor ->
                        writeDescriptorBytes(
                            gatt = gatt,
                            descriptor = descriptor,
                            value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                        )
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                if (characteristic.uuid == dataUuid) {
                    LupinePacketCodec.decodeStatus(value)?.let { telemetry ->
                        gateway.onTelemetryReceived(telemetry)
                    }
                }
            }

            @Suppress("DEPRECATION")
            @Deprecated("Deprecated in Android 13")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && characteristic.uuid == dataUuid) {
                    characteristic.value?.let { bytes ->
                        LupinePacketCodec.decodeStatus(bytes)?.let { telemetry ->
                            gateway.onTelemetryReceived(telemetry)
                        }
                    }
                }
            }
        }

        return device.connectGatt(this, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Suppress("DEPRECATION")
    private fun writeCharacteristicBytes(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            )
        } else {
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @Suppress("DEPRECATION")
    private fun writeDescriptorBytes(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            descriptor.value = value
            gatt.writeDescriptor(descriptor)
        }
    }

    companion object {
        const val EXTENSION_ID = "karoo-lupine-piko"
        private const val TAG = "PIKO_SCAN"
        private const val UID_PREFIX = "piko-"
        private const val DEFAULT_DISPLAY_NAME = "Lupine Piko"
        private const val CCCD_UUID = "00002902-0000-1000-8000-00805F9B34FB"

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
