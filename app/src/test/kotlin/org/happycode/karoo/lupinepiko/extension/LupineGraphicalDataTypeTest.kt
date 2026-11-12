package org.happycode.karoo.lupinepiko.extension

import android.content.Context
import android.widget.RemoteViews
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.happycode.karoo.lupinepiko.adapters.ble.DefaultLupineBleGateway
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState

@OptIn(ExperimentalCoroutinesApi::class)
class LupineGraphicalDataTypeTest : ShouldSpec({

    fun createConfig(gridSize: Pair<Int, Int> = 30 to 15) = ViewConfig(
        gridSize = gridSize,
        viewSize = 200 to 160,
        textSize = 24,
        preview = false,
    )

    should("hide standard header and update RemoteViews promptly when gateway state changes") {
        // given
        val gateway = DefaultLupineBleGateway(
            initialState = LightState(currentMode = LightMode.MAX, isConnected = true),
        )
        val renderedModes = mutableListOf<LightMode>()
        val remoteViews = mockk<RemoteViews>(relaxed = true)
        val dataType = LupineGraphicalDataType(
            extension = LupinePikoExtension.EXTENSION_ID,
            typeId = LupineGraphicalDataType.TYPE_LIGHT_CONTROL,
            gateway = gateway,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            renderer = LupineRemoteViewsRenderer { _, state, _ ->
                renderedModes.add(state.currentMode)
                remoteViews
            },
            minUpdateIntervalMs = 0L,
        )
        val context = mockk<Context>(relaxed = true)
        val emitter = mockk<ViewEmitter>(relaxed = true)
        val cancellableSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancellableSlot)) } returns Unit

        // when
        dataType.startView(context = context, config = createConfig(), emitter = emitter)
        gateway.applyMode(LightMode.DIMMED)
        cancellableSlot.captured.invoke()
        gateway.applyMode(LightMode.HIGH)

        // then
        verify(exactly = 1) { emitter.onNext(UpdateGraphicConfig(showHeader = false)) }
        verify(exactly = 2) { emitter.updateView(remoteViews) }
        renderedModes shouldBe listOf(LightMode.MAX, LightMode.DIMMED)
    }

    should("delay rapid consecutive view updates until minUpdateIntervalMs has elapsed") {
        // given
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)
        val gateway = DefaultLupineBleGateway(
            initialState = LightState(currentMode = LightMode.MAX, isConnected = true),
        )
        val renderedModes = mutableListOf<LightMode>()
        val remoteViews = mockk<RemoteViews>(relaxed = true)
        val dataType = LupineGraphicalDataType(
            extension = LupinePikoExtension.EXTENSION_ID,
            typeId = LupineGraphicalDataType.TYPE_BRIGHTNESS_BAR,
            gateway = gateway,
            scope = testScope,
            renderer = LupineRemoteViewsRenderer { _, state, _ ->
                renderedModes.add(state.currentMode)
                remoteViews
            },
            minUpdateIntervalMs = 950L,
            clockMs = { testDispatcher.scheduler.currentTime },
        )
        val emitter = mockk<ViewEmitter>(relaxed = true)
        val cancellableSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancellableSlot)) } returns Unit

        // when
        dataType.startView(context = mockk(relaxed = true), config = createConfig(), emitter = emitter)
        testScope.runCurrent()

        gateway.applyMode(LightMode.LOW)
        testScope.advanceTimeBy(200L)
        gateway.applyMode(LightMode.DIMMED)
        testScope.runCurrent()

        // then - throttled before 950ms elapses
        renderedModes shouldBe listOf(LightMode.MAX)

        // when - cooldown completes
        testScope.advanceTimeBy(750L)
        testScope.runCurrent()
        cancellableSlot.captured.invoke()

        // then - latest conflated mode is rendered
        renderedModes shouldBe listOf(LightMode.MAX, LightMode.DIMMED)
        verify(exactly = 2) { emitter.updateView(remoteViews) }
    }

    should("emit Searching when disconnected and Streaming wattage when connected") {
        // given
        val gateway = DefaultLupineBleGateway(initialState = LightState(isConnected = false))
        val dataType = LupineGraphicalDataType(
            extension = LupinePikoExtension.EXTENSION_ID,
            typeId = LupineGraphicalDataType.TYPE_LIGHT_CONTROL,
            gateway = gateway,
            scope = CoroutineScope(UnconfinedTestDispatcher()),
            renderer = LupineRemoteViewsRenderer { _, _, _ -> mockk(relaxed = true) },
            minUpdateIntervalMs = 0L,
        )
        val emitted = mutableListOf<StreamState>()
        val emitter = mockk<Emitter<StreamState>>(relaxed = true)
        val cancellableSlot = slot<() -> Unit>()
        every { emitter.onNext(capture(emitted)) } returns Unit
        every { emitter.setCancellable(capture(cancellableSlot)) } returns Unit

        // when
        dataType.startStream(emitter)
        gateway.onConnectionChanged(true)
        gateway.applyMode(LightMode.HIGH)
        cancellableSlot.captured.invoke()

        // then
        emitted[0] shouldBe StreamState.Searching
        val lastStreaming = emitted.last() as StreamState.Streaming
        lastStreaming.dataPoint.dataTypeId shouldBe DataType.dataTypeId(
            LupinePikoExtension.EXTENSION_ID,
            LupineGraphicalDataType.TYPE_LIGHT_CONTROL,
        )
        lastStreaming.dataPoint.values[DataType.Field.SINGLE] shouldBe LightMode.HIGH.watts
    }
})
