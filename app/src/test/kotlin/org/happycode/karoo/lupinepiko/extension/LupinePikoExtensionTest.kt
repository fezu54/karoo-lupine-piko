package org.happycode.karoo.lupinepiko.extension

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.OnConnectionStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.happycode.karoo.lupinepiko.adapters.ble.DefaultLupineBleGateway
import org.happycode.karoo.lupinepiko.adapters.ble.LupineBleManager
import org.happycode.karoo.lupinepiko.adapters.ble.LupinePacketCodec
import org.happycode.karoo.lupinepiko.adapters.storage.SharedPrefsLightConfigRepository
import org.happycode.karoo.lupinepiko.application.CycleLightModeUseCase
import org.happycode.karoo.lupinepiko.application.QuickDimToggleUseCase
import org.happycode.karoo.lupinepiko.application.TogglePowerUseCase
import org.happycode.karoo.lupinepiko.domain.BleConnectionState
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState

@OptIn(ExperimentalCoroutinesApi::class)
class LupinePikoExtensionTest : ShouldSpec({

    lateinit var extension: LupinePikoExtension
    lateinit var gateway: DefaultLupineBleGateway

    fun createTestExtension(
        bluetoothStateFlow: Flow<Boolean> = flowOf(true),
        scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
        bleManagerFactory: (String, CoroutineScope) -> LupineBleManager = { _, _ -> mockk(relaxed = true) },
    ): LupinePikoExtension {
        gateway = DefaultLupineBleGateway(initialState = LightState(currentMode = LightMode.MAX))
        val configRepo = SharedPrefsLightConfigRepository()
        return LupinePikoExtension(
            defaultScope = scope,
            scanSettingsFactory = { mockk(relaxed = true) },
            bluetoothStateFlowFactory = { bluetoothStateFlow },
            bleManagerFactory = bleManagerFactory,
            gateway = gateway,
            quickDimToggleUseCase = QuickDimToggleUseCase(gateway = gateway, configRepository = configRepo),
            cycleLightModeUseCase = CycleLightModeUseCase(gateway = gateway, configRepository = configRepo),
            togglePowerUseCase = TogglePowerUseCase(gateway = gateway, configRepository = configRepo),
        )
    }

    fun setupScanningEnvironment(
        spyExtension: LupinePikoExtension
    ): Pair<ScanCallback, Emitter<Device>> {
        val bluetoothManager = mockk<BluetoothManager>()
        val bluetoothAdapter = mockk<BluetoothAdapter>()
        val scanner = mockk<BluetoothLeScanner>(relaxed = true)
        val emitter = mockk<Emitter<Device>>(relaxed = true)
        val scanCallbackSlot = slot<ScanCallback>()

        every { spyExtension.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
        every { spyExtension.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
        every { bluetoothManager.adapter } returns bluetoothAdapter
        every { bluetoothAdapter.isEnabled } returns true
        every { bluetoothAdapter.bluetoothLeScanner } returns scanner
        every { scanner.startScan(any<List<ScanFilter>>(), any(), capture(scanCallbackSlot)) } returns Unit

        spyExtension.startScan(emitter)
        return scanCallbackSlot.captured to emitter
    }

    beforeEach {
        extension = createTestExtension()
    }

    afterEach {
        unmockkAll()
    }

    should("handle missing permission when start scan is called") {
        // given
        val spyExtension = spyk(extension)
        every { spyExtension.checkSelfPermission(any()) } returns PackageManager.PERMISSION_DENIED
        val emitter = mockk<Emitter<Device>>(relaxed = true)

        // when
        spyExtension.startScan(emitter)

        // then
        verify { emitter.setCancellable(any()) }
    }

    should("match and emit device when scan result has Lupine Piko name") {
        // given
        val spyExtension = spyk(extension)
        val (scanCallback, emitter) = setupScanningEnvironment(spyExtension)

        val result = mockk<ScanResult>(relaxed = true) {
            every { device.address } returns "11:22:33:44:55:66"
            every { device.name } returns "Lupine Piko"
            every { scanRecord?.deviceName } returns "Lupine Piko"
        }

        // when
        scanCallback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)

        // then
        verify {
            emitter.onNext(
                match {
                    it.uid == "piko-11:22:33:44:55:66" &&
                        it.dataTypes == listOf(
                            DataType.dataTypeId(
                                LupinePikoExtension.EXTENSION_ID,
                                LupineGraphicalDataType.TYPE_LIGHT_CONTROL,
                            ),
                            DataType.dataTypeId(
                                LupinePikoExtension.EXTENSION_ID,
                                LupineGraphicalDataType.TYPE_BRIGHTNESS_BAR,
                            ),
                        )
                }
            )
        }
    }

    should("not emit device when scan result does not match Lupine Piko criteria") {
        // given
        val spyExtension = spyk(extension)
        val (scanCallback, emitter) = setupScanningEnvironment(spyExtension)

        val result = mockk<ScanResult>(relaxed = true) {
            every { device.address } returns "99:99:99:99:99:99"
            every { device.name } returns "OtherDevice"
            every { scanRecord?.deviceName } returns "OtherDevice"
        }

        // when
        scanCallback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)

        // then
        verify(exactly = 0) { emitter.onNext(any()) }
    }

    should("pause scan when bluetooth transitions to disabled") {
        // given
        val bluetoothStateFlow = MutableStateFlow(true)
        val extensionWithFlow = createTestExtension(
            bluetoothStateFlow = bluetoothStateFlow,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )
        val spyExtension = spyk(extensionWithFlow)
        val bluetoothManager = mockk<BluetoothManager>()
        val bluetoothAdapter = mockk<BluetoothAdapter>()
        val scanner = mockk<BluetoothLeScanner>(relaxed = true)
        val emitter = mockk<Emitter<Device>>(relaxed = true)

        every { spyExtension.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
        every { spyExtension.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
        every { bluetoothManager.adapter } returns bluetoothAdapter
        every { bluetoothAdapter.isEnabled } returns true
        every { bluetoothAdapter.bluetoothLeScanner } returns scanner
        every { scanner.startScan(any<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) } returns Unit
        every { scanner.stopScan(any<ScanCallback>()) } returns Unit

        // when
        spyExtension.startScan(emitter)

        // then
        verify(exactly = 1) { scanner.startScan(any<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }

        // when
        bluetoothStateFlow.value = false

        // then
        verify(exactly = 1) { scanner.stopScan(any<ScanCallback>()) }
    }

    should("register cancellable and start bleManager when connectDevice is called") {
        // given
        val bleManager = mockk<LupineBleManager>(relaxed = true) {
            every { connectionState } returns MutableStateFlow(BleConnectionState.DISCONNECTED)
            every { notificationsEnabled } returns MutableSharedFlow()
            every { incomingData } returns MutableSharedFlow()
        }
        val extensionWithBle = createTestExtension(bleManagerFactory = { _, _ -> bleManager })
        val emitter = mockk<Emitter<DeviceEvent>>(relaxed = true)

        // when
        extensionWithBle.connectDevice("piko-11:22:33:44:55:66", emitter)

        // then
        verify { emitter.setCancellable(any()) }
        verify(exactly = 1) { bleManager.start() }
    }

    should("forward connection state telemetry and commands between bleManager gateway and emitter") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val connectionStateFlow = MutableStateFlow(BleConnectionState.DISCONNECTED)
            val notificationsEnabledFlow = MutableSharedFlow<Unit>()
            val incomingDataFlow = MutableSharedFlow<ByteArray>()
            val bleManager = mockk<LupineBleManager>(relaxed = true) {
                every { connectionState } returns connectionStateFlow
                every { notificationsEnabled } returns notificationsEnabledFlow
                every { incomingData } returns incomingDataFlow
            }
            val extensionWithBle = createTestExtension(
                scope = backgroundScope,
                bleManagerFactory = { _, _ -> bleManager },
            )
            val emitter = mockk<Emitter<DeviceEvent>>(relaxed = true)
            val cancelSlot = slot<() -> Unit>()
            every { emitter.setCancellable(capture(cancelSlot)) } returns Unit

            // when
            extensionWithBle.connectDevice("piko-11:22:33:44:55:66", emitter)
            connectionStateFlow.value = BleConnectionState.SEARCHING
            connectionStateFlow.value = BleConnectionState.CONNECTED
            notificationsEnabledFlow.emit(Unit)

            val statusPacket = byteArrayOf(
                0x74, 0x26, 0x05, 0x20, 0x04, 100, 0, 90,
                116, 0, 125, 0, 1, 232.toByte(), 0, 0,
            )
            incomingDataFlow.emit(statusPacket)
            gateway.applyMode(LightMode.DIMMED)

            // then
            verify { emitter.onNext(OnConnectionStatus(ConnectionStatus.SEARCHING)) }
            verify { emitter.onNext(OnConnectionStatus(ConnectionStatus.CONNECTED)) }
            gateway.state.value.isConnected shouldBe true
            gateway.state.value.telemetry?.softwareRevision shouldBe 5
            coVerify { bleManager.writeCommand(LupinePacketCodec.encodeStatusPoll()) }
            coVerify {
                bleManager.writeCommand(
                    LupinePacketCodec.encodeDirectMode(
                        mode = LightMode.DIMMED,
                        softwareRevision = 5,
                    ),
                )
            }

            // when cancelled
            cancelSlot.captured.invoke()

            // then
            verify(exactly = 1) { bleManager.stop() }
            gateway.state.value.isConnected shouldBe false
        }
    }

    should("not wipe newer connection state or command sender when older connectDevice is cancelled") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val firstStateFlow = MutableStateFlow(BleConnectionState.DISCONNECTED)
            val secondStateFlow = MutableStateFlow(BleConnectionState.DISCONNECTED)
            val firstManager = mockk<LupineBleManager>(relaxed = true) {
                every { connectionState } returns firstStateFlow
                every { notificationsEnabled } returns MutableSharedFlow()
                every { incomingData } returns MutableSharedFlow()
            }
            val secondManager = mockk<LupineBleManager>(relaxed = true) {
                every { connectionState } returns secondStateFlow
                every { notificationsEnabled } returns MutableSharedFlow()
                every { incomingData } returns MutableSharedFlow()
            }
            var invocationCount = 0
            val extensionWithBle = createTestExtension(
                scope = backgroundScope,
                bleManagerFactory = { _, _ ->
                    invocationCount++
                    if (invocationCount == 1) firstManager else secondManager
                },
            )
            val firstEmitter = mockk<Emitter<DeviceEvent>>(relaxed = true)
            val secondEmitter = mockk<Emitter<DeviceEvent>>(relaxed = true)
            val firstCancelSlot = slot<() -> Unit>()
            every { firstEmitter.setCancellable(capture(firstCancelSlot)) } returns Unit

            // when
            extensionWithBle.connectDevice("piko-11:22:33:44:55:66", firstEmitter)
            extensionWithBle.connectDevice("piko-11:22:33:44:55:66", secondEmitter)
            secondStateFlow.value = BleConnectionState.CONNECTED

            firstCancelSlot.captured.invoke()
            gateway.applyMode(LightMode.DIMMED)

            // then
            verify(exactly = 1) { firstManager.stop() }
            verify(exactly = 0) { secondManager.stop() }
            gateway.state.value.isConnected shouldBe true
            coVerify(exactly = 0) { firstManager.writeCommand(any()) }
            coVerify(exactly = 1) {
                secondManager.writeCommand(LupinePacketCodec.encodeDirectMode(mode = LightMode.DIMMED))
            }
        }
    }

    should("ignore connectDevice when uid has no valid MAC address") {
        // given
        var factoryCalled = false
        val extensionWithBle = createTestExtension(
            bleManagerFactory = { _, _ ->
                factoryCalled = true
                mockk(relaxed = true)
            },
        )
        val emitter = mockk<Emitter<DeviceEvent>>(relaxed = true)

        // when
        extensionWithBle.connectDevice("invalid-uid", emitter)

        // then
        factoryCalled shouldBe false
        verify { emitter.setCancellable(any()) }
    }

    should("execute quick dim cycle and power actions when handleAction is called") {
        // when
        extension.handleAction(LupinePikoExtension.ACTION_TOGGLE_DIM)
        val afterDim = gateway.state.value.currentMode

        extension.handleAction(LupinePikoExtension.ACTION_TOGGLE_DIM)
        val afterRestore = gateway.state.value.currentMode

        extension.handleAction(LupinePikoExtension.ACTION_CYCLE_MODE)
        val afterCycle = gateway.state.value.currentMode

        extension.handleAction(LupinePikoExtension.ACTION_TOGGLE_POWER)
        val afterPowerOff = gateway.state.value.currentMode

        // then
        afterDim shouldBe LightMode.DIMMED
        afterRestore shouldBe LightMode.MAX
        afterCycle shouldBe LightMode.DIMMED
        afterPowerOff shouldBe LightMode.OFF
    }

    should("expose light control and brightness bar graphical data types") {
        // then
        extension.types.map { it.typeId } shouldBe listOf(
            LupineGraphicalDataType.TYPE_LIGHT_CONTROL,
            LupineGraphicalDataType.TYPE_BRIGHTNESS_BAR,
        )
    }

    should("delegate onBonusAction to handleAction") {
        // when
        extension.onBonusAction(LupinePikoExtension.ACTION_TOGGLE_DIM)

        // then
        gateway.state.value.currentMode shouldBe LightMode.DIMMED
    }

    should("parse MAC address from uid and match Lupine device names") {
        // then
        LupinePikoExtension.extractMacAddress("piko-11:22:33:44:55:66") shouldBe "11:22:33:44:55:66"
        LupinePikoExtension.extractMacAddress("invalid-uid") shouldBe null
        LupinePikoExtension.isLupineDeviceName("Lupine Piko R") shouldBe true
        LupinePikoExtension.isLupineDeviceName("Garmin Varia") shouldBe false
    }
})
