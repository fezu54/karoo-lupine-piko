package org.happycode.karoo.lupinepiko.extension

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.util.Log
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.happycode.karoo.lupinepiko.adapters.ble.DefaultLupineBleGateway
import org.happycode.karoo.lupinepiko.adapters.storage.SharedPrefsLightConfigRepository
import org.happycode.karoo.lupinepiko.application.CycleLightModeUseCase
import org.happycode.karoo.lupinepiko.application.QuickDimToggleUseCase
import org.happycode.karoo.lupinepiko.application.TogglePowerUseCase
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState

@OptIn(ExperimentalCoroutinesApi::class)
class LupinePikoExtensionTest : ShouldSpec({

    lateinit var extension: LupinePikoExtension
    lateinit var gateway: DefaultLupineBleGateway

    fun mockLogCalls() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
    }

    fun createTestExtension(
        bluetoothStateFlow: Flow<Boolean> = flowOf(true),
        scope: CoroutineScope = CoroutineScope(UnconfinedTestDispatcher()),
    ): LupinePikoExtension {
        gateway = DefaultLupineBleGateway(initialState = LightState(currentMode = LightMode.MAX))
        val configRepo = SharedPrefsLightConfigRepository()
        return LupinePikoExtension(
            defaultScope = scope,
            scanSettingsFactory = { mockk(relaxed = true) },
            bluetoothStateFlowFactory = { bluetoothStateFlow },
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
        mockLogCalls()
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
        verify { emitter.onNext(match { it.uid == "piko-11:22:33:44:55:66" }) }
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

    should("register cancellable when connectDevice is called") {
        // given
        val emitter = mockk<Emitter<DeviceEvent>>(relaxed = true)

        // when
        extension.connectDevice("piko-11:22:33:44:55:66", emitter)

        // then
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

    should("parse MAC address from uid and match Lupine device names") {
        // then
        LupinePikoExtension.extractMacAddress("piko-11:22:33:44:55:66") shouldBe "11:22:33:44:55:66"
        LupinePikoExtension.extractMacAddress("invalid-uid") shouldBe null
        LupinePikoExtension.isLupineDeviceName("Lupine Piko R") shouldBe true
        LupinePikoExtension.isLupineDeviceName("Garmin Varia") shouldBe false
    }
})
