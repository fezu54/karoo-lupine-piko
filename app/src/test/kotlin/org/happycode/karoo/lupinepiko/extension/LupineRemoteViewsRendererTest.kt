package org.happycode.karoo.lupinepiko.extension

import android.app.PendingIntent
import android.content.Context
import android.widget.RemoteViews
import io.hammerhead.karooext.models.ViewConfig
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.happycode.karoo.lupinepiko.R
import org.happycode.karoo.lupinepiko.application.BrightnessStepDirection
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState
import org.happycode.karoo.lupinepiko.domain.LightTelemetry
import org.happycode.karoo.lupinepiko.domain.LupineModel

class LupineRemoteViewsRendererTest : ShouldSpec({

    fun mockContext(): Context = mockk<Context>(relaxed = true) {
        every { getString(R.string.tile_status_high_beam) } returns "HIGH BEAM"
        every { getString(R.string.tile_status_dimmed) } returns "DIMMED"
        every { getString(R.string.tile_status_disconnected) } returns "DISCONNECTED"
        every { getString(R.string.tile_status_off) } returns "OFF"
        every { getString(R.string.tile_battery_placeholder) } returns "--.-V"
        every { getColor(R.color.karoo_status_warn) } returns 0x111111
        every { getColor(R.color.karoo_status_ok) } returns 0x222222
        every { getColor(R.color.karoo_text_secondary) } returns 0x333333
        every { getColor(R.color.karoo_text_primary) } returns 0x444444
        every { getColor(R.color.karoo_battery_good) } returns 0x555555
        every { getColor(R.color.karoo_battery_medium) } returns 0x666666
        every { getColor(R.color.karoo_battery_low) } returns 0x777777
        every { getColor(R.color.karoo_segment_high_beam) } returns 0x888888
        every { getColor(R.color.karoo_segment_active) } returns 0x999999
        every { getColor(R.color.karoo_segment_inactive) } returns 0xAAAAAA
        every { getColor(R.color.karoo_segment_text_on_active) } returns 0xBBBBBB
    }

    fun connectedState(mode: LightMode, voltage: Double = 8.1) = LightState(
        currentMode = mode,
        isConnected = true,
        telemetry = LightTelemetry(
            model = LupineModel.PIKO_R_2019,
            hardwareRevision = 6,
            softwareRevision = 1,
            isLampOff = mode == LightMode.OFF,
            batteryVoltage = voltage,
            ledCurrentAmps = 1.5,
            maxCurrentAmps = 2.0,
            consumedAh = 0.2,
            temperatureCelsius = 25.0,
        ),
    )

    should("bind high beam state and quick dim click PendingIntent for control tile") {
        // given
        val context = mockContext()
        val remoteViews = mockk<RemoteViews>(relaxed = true)
        val pendingIntent = mockk<PendingIntent>()
        var capturedLayoutId = 0
        val renderer = LupineControlRemoteViewsRenderer(
            remoteViewsFactory = { _, layoutId ->
                capturedLayoutId = layoutId
                remoteViews
            },
            quickDimPendingIntentFactory = { pendingIntent },
        )
        val config = ViewConfig(gridSize = 60 to 15, viewSize = 440 to 160, textSize = 24)

        // when
        renderer.render(context = context, state = connectedState(LightMode.MAX), config = config)

        // then
        capturedLayoutId shouldBe R.layout.remote_lupine_control_wide
        verify { remoteViews.setTextViewText(R.id.lupine_mode_text, "MAX 20W") }
        verify { remoteViews.setTextViewText(R.id.lupine_status_label, "HIGH BEAM") }
        verify { remoteViews.setTextViewText(R.id.lupine_battery_text, "8.1V") }
        verify { remoteViews.setInt(R.id.lupine_high_beam_icon, "setColorFilter", 0x111111) }
        verify { remoteViews.setOnClickPendingIntent(R.id.lupine_tile_root, pendingIntent) }
    }

    should("bind dimmed status and tall layout for control tile") {
        // given
        val context = mockContext()
        val remoteViews = mockk<RemoteViews>(relaxed = true)
        var capturedLayoutId = 0
        val renderer = LupineControlRemoteViewsRenderer(
            remoteViewsFactory = { _, layoutId ->
                capturedLayoutId = layoutId
                remoteViews
            },
            quickDimPendingIntentFactory = { mockk() },
        )
        val config = ViewConfig(gridSize = 30 to 30, viewSize = 220 to 320, textSize = 24)

        // when
        renderer.render(context = context, state = connectedState(LightMode.DIMMED), config = config)

        // then
        capturedLayoutId shouldBe R.layout.remote_lupine_control_tall
        verify { remoteViews.setTextViewText(R.id.lupine_mode_text, "DIM 0.5W") }
        verify { remoteViews.setTextViewText(R.id.lupine_status_label, "DIMMED") }
        verify { remoteViews.setInt(R.id.lupine_high_beam_icon, "setColorFilter", 0x222222) }
    }

    should("bind compact stepper buttons and progress bar for 1x1 brightness bar") {
        // given
        val context = mockContext()
        val remoteViews = mockk<RemoteViews>(relaxed = true)
        val downIntent = mockk<PendingIntent>()
        val upIntent = mockk<PendingIntent>()
        var capturedLayoutId = 0
        val renderer = LupineBrightnessBarRemoteViewsRenderer(
            remoteViewsFactory = { _, layoutId ->
                capturedLayoutId = layoutId
                remoteViews
            },
            stepPendingIntentFactory = { _, direction ->
                when (direction) {
                    BrightnessStepDirection.DOWN -> downIntent
                    BrightnessStepDirection.UP -> upIntent
                }
            },
            setModePendingIntentFactory = { _, _ -> mockk() },
        )
        val config = ViewConfig(gridSize = 30 to 15, viewSize = 220 to 160, textSize = 20)

        // when
        renderer.render(context = context, state = connectedState(LightMode.MEDIUM), config = config)

        // then
        capturedLayoutId shouldBe R.layout.remote_lupine_brightness_compact
        verify { remoteViews.setTextViewText(R.id.lupine_mode_text, "MED 5W") }
        verify { remoteViews.setProgressBar(R.id.lupine_brightness_progress, 100, 60, false) }
        verify { remoteViews.setOnClickPendingIntent(R.id.lupine_btn_step_down, downIntent) }
        verify { remoteViews.setOnClickPendingIntent(R.id.lupine_btn_step_up, upIntent) }
    }

    should("bind five clickable brightness segments for 2x1 brightness bar") {
        // given
        val context = mockContext()
        val remoteViews = mockk<RemoteViews>(relaxed = true)
        val modeIntents = LightMode.defaultCycleModes.associateWith { mockk<PendingIntent>() }
        var capturedLayoutId = 0
        val renderer = LupineBrightnessBarRemoteViewsRenderer(
            remoteViewsFactory = { _, layoutId ->
                capturedLayoutId = layoutId
                remoteViews
            },
            stepPendingIntentFactory = { _, _ -> mockk() },
            setModePendingIntentFactory = { _, mode -> modeIntents.getValue(mode) },
        )
        val config = ViewConfig(gridSize = 60 to 15, viewSize = 440 to 160, textSize = 20)

        // when
        renderer.render(context = context, state = connectedState(LightMode.HIGH), config = config)

        // then
        capturedLayoutId shouldBe R.layout.remote_lupine_brightness_wide
        verify { remoteViews.setTextViewText(R.id.lupine_segment_0, "DIM") }
        verify { remoteViews.setTextViewText(R.id.lupine_segment_3, "HIGH") }
        verify { remoteViews.setTextViewText(R.id.lupine_segment_4, "MAX") }
        verify { remoteViews.setInt(R.id.lupine_segment_3, "setBackgroundColor", 0x888888) }
        verify { remoteViews.setInt(R.id.lupine_segment_4, "setBackgroundColor", 0xAAAAAA) }
        verify {
            remoteViews.setOnClickPendingIntent(
                R.id.lupine_segment_0,
                modeIntents.getValue(LightMode.DIMMED),
            )
        }
        verify {
            remoteViews.setOnClickPendingIntent(
                R.id.lupine_segment_4,
                modeIntents.getValue(LightMode.MAX),
            )
        }
    }
})
