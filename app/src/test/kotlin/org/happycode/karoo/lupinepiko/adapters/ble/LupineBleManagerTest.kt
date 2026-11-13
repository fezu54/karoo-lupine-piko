package org.happycode.karoo.lupinepiko.adapters.ble

import com.juul.kable.Advertisement
import com.juul.kable.DiscoveredCharacteristic
import com.juul.kable.DiscoveredService
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.WriteType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.happycode.karoo.lupinepiko.domain.BleConnectionState

@OptIn(ExperimentalCoroutinesApi::class)
class LupineBleManagerTest : ShouldSpec({

    data class LupineTestService(
        val service: DiscoveredService,
        val commandChar: DiscoveredCharacteristic?,
        val dataChar: DiscoveredCharacteristic?,
    )

    val address = "11:22:33:44:55:66"

    lateinit var scanner: Scanner<Advertisement>
    lateinit var peripheral: Peripheral
    lateinit var advertisementsFlow: MutableSharedFlow<Advertisement>
    lateinit var peripheralStateFlow: MutableStateFlow<State>
    lateinit var servicesFlow: MutableStateFlow<List<DiscoveredService>?>
    lateinit var notificationsFlow: MutableSharedFlow<ByteArray>
    lateinit var onSubscriptionSlot: CapturingSlot<suspend () -> Unit>

    fun createLupineService(
        serviceUuid: String = LupinePacketCodec.SERVICE_LUPINE_UUID,
        includeCommandChar: Boolean = true,
        includeDataChar: Boolean = true,
    ): LupineTestService {
        val commandChar = mockk<DiscoveredCharacteristic> {
            every { characteristicUuid.toString() } returns LupinePacketCodec.CHAR_NOTIFY_COMMAND_UUID.lowercase()
        }.takeIf { includeCommandChar }

        val dataChar = mockk<DiscoveredCharacteristic> {
            every { characteristicUuid.toString() } returns LupinePacketCodec.CHAR_DATA_STATUS_UUID.lowercase()
        }.takeIf { includeDataChar }

        val service = mockk<DiscoveredService> {
            every { this@mockk.serviceUuid.toString() } returns serviceUuid.lowercase()
            every { characteristics } returns listOfNotNull(commandChar, dataChar)
        }
        return LupineTestService(
            service = service,
            commandChar = commandChar,
            dataChar = dataChar,
        )
    }

    beforeEach {
        advertisementsFlow = MutableSharedFlow(replay = 1)
        peripheralStateFlow = MutableStateFlow(mockk<State.Disconnected>())
        servicesFlow = MutableStateFlow(null)
        notificationsFlow = MutableSharedFlow()
        onSubscriptionSlot = slot()

        scanner = mockk {
            every { advertisements } returns advertisementsFlow
        }
        peripheral = mockk(relaxed = true) {
            every { state } returns peripheralStateFlow
            every { services } returns servicesFlow
            every { observe(any(), capture(onSubscriptionSlot)) } returns notificationsFlow
        }
    }

    afterEach {
        unmockkAll()
    }

    should("start in disconnected state") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = LupineBleManager(
                address = address,
                scope = backgroundScope,
                scanner = scanner,
                peripheralFactory = { peripheral },
            )

            // then
            manager.connectionState.value shouldBe BleConnectionState.DISCONNECTED
        }
    }

    should("transition to searching when started and waiting for advertisement") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = LupineBleManager(
                address = address,
                scope = backgroundScope,
                scanner = scanner,
                peripheralFactory = { peripheral },
            )

            // when
            manager.start()

            // then
            manager.connectionState.value shouldBe BleConnectionState.SEARCHING
            manager.stop()
        }
    }

    should("emit notificationsEnabled only after subscription and forward telemetry notifications") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = LupineBleManager(
                address = address,
                scope = backgroundScope,
                scanner = scanner,
                peripheralFactory = { peripheral },
            )
            val testService = createLupineService()
            val notificationsDeferred = async { manager.notificationsEnabled.first() }
            val incomingDeferred = async { manager.incomingData.first() }

            // when
            manager.start()
            advertisementsFlow.emit(mockk())
            peripheralStateFlow.value = mockk<State.Connected>()
            servicesFlow.value = listOf(testService.service)

            // then: notificationsEnabled waits until CCCD subscription completes
            notificationsDeferred.isCompleted shouldBe false

            // when
            onSubscriptionSlot.captured.invoke()
            val expectedPayload = byteArrayOf(0x74, 0x26, 0x05)
            notificationsFlow.emit(expectedPayload)

            // then
            manager.connectionState.value shouldBe BleConnectionState.CONNECTED
            notificationsDeferred.await() shouldBe Unit
            incomingDeferred.await() shouldBe expectedPayload
            manager.stop()
        }
    }

    should("write command through peripheral when connected") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = LupineBleManager(
                address = address,
                scope = backgroundScope,
                scanner = scanner,
                peripheralFactory = { peripheral },
            )
            val testService = createLupineService()
            val commandChar = requireNotNull(testService.commandChar)
            manager.start()
            advertisementsFlow.emit(mockk())
            peripheralStateFlow.value = mockk<State.Connected>()
            servicesFlow.value = listOf(testService.service)

            val payload = LupinePacketCodec.encodeStatusPoll()

            // when
            manager.writeCommand(payload)

            // then
            coVerify(exactly = 1) {
                peripheral.write(commandChar, payload, WriteType.WithResponse)
            }
            manager.stop()
        }
    }

    should("fail write command when disconnected or command characteristic missing") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = LupineBleManager(
                address = address,
                scope = backgroundScope,
                scanner = scanner,
                peripheralFactory = { peripheral },
            )

            // when & then
            shouldThrow<IllegalStateException> {
                manager.writeCommand(LupinePacketCodec.encodeStatusPoll())
            }
            coVerify(exactly = 0) { peripheral.write(any(), any(), any()) }
        }
    }

    should("rethrow peripheral write exception so gateway can report failure") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = LupineBleManager(
                address = address,
                scope = backgroundScope,
                scanner = scanner,
                peripheralFactory = { peripheral },
            )
            val testService = createLupineService()
            coEvery { peripheral.write(any(), any(), any()) } throws IllegalStateException("GATT write failed")

            manager.start()
            advertisementsFlow.emit(mockk())
            peripheralStateFlow.value = mockk<State.Connected>()
            servicesFlow.value = listOf(testService.service)

            // when & then
            shouldThrow<IllegalStateException> {
                manager.writeCommand(LupinePacketCodec.encodeStatusPoll())
            }
            coVerify(exactly = 1) { peripheral.write(any(), any(), WriteType.WithResponse) }
            manager.stop()
        }
    }

    should("close previous peripheral and reconnect after disconnect") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val firstPeripheral = peripheral
            val secondPeripheral = mockk<Peripheral>(relaxed = true) {
                every { state } returns peripheralStateFlow
                every { services } returns servicesFlow
                every { observe(any(), any()) } returns notificationsFlow
            }
            val createdPeripherals = mutableListOf<Peripheral>()
            val manager = LupineBleManager(
                address = address,
                scope = backgroundScope,
                scanner = scanner,
                peripheralFactory = {
                    val next = if (createdPeripherals.isEmpty()) firstPeripheral else secondPeripheral
                    createdPeripherals.add(next)
                    next
                },
            )
            val testService = createLupineService()

            manager.start()
            advertisementsFlow.emit(mockk())
            peripheralStateFlow.value = mockk<State.Connected>()
            servicesFlow.value = listOf(testService.service)
            createdPeripherals.size shouldBe 1

            // when
            peripheralStateFlow.value = mockk<State.Disconnected>()
            runCurrent()

            // then
            coVerify(exactly = 1) { firstPeripheral.disconnect() }
            coVerify(exactly = 1) { firstPeripheral.close() }
            manager.connectionState.value shouldBe BleConnectionState.DISCONNECTED

            // when
            advanceTimeBy(5_000L)
            runCurrent()

            // then
            createdPeripherals.size shouldBe 2
            manager.stop()
        }
    }

    should("disconnect and close peripheral on stop") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = LupineBleManager(
                address = address,
                scope = backgroundScope,
                scanner = scanner,
                peripheralFactory = { peripheral },
            )
            manager.start()
            advertisementsFlow.emit(mockk())
            peripheralStateFlow.value = mockk<State.Connected>()
            coEvery { peripheral.disconnect() } returns Unit

            // when
            manager.stop()

            // then
            manager.connectionState.value shouldBe BleConnectionState.DISCONNECTED
            coVerify(exactly = 1) { peripheral.disconnect() }
            coVerify(exactly = 1) { peripheral.close() }
        }
    }
})
