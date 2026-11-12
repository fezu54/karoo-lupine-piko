package org.happycode.karoo.lupinepiko.extension

import android.app.PendingIntent
import android.content.Context
import android.widget.RemoteViews
import io.hammerhead.karooext.models.ViewConfig
import org.happycode.karoo.lupinepiko.R
import org.happycode.karoo.lupinepiko.application.BrightnessStepDirection
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState

fun interface LupineRemoteViewsRenderer {
    fun render(context: Context, state: LightState, config: ViewConfig): RemoteViews
}

internal object RemoteViewsBindingSupport {
    fun bindHeader(
        context: Context,
        views: RemoteViews,
        header: LupineTileHeader,
        includeStatusAndIcon: Boolean = true,
    ) {
        val batteryText = header.batteryVoltageLabel ?: context.getString(R.string.tile_battery_placeholder)
        val batteryColor = context.getColor(resolveBatteryColorRes(header.batteryStatus))
        views.setTextViewText(R.id.lupine_mode_text, header.modeWithWattsLabel)
        views.setTextViewText(R.id.lupine_battery_text, batteryText)
        views.setTextColor(R.id.lupine_battery_text, batteryColor)

        if (includeStatusAndIcon) {
            val statusLabel = context.getString(resolveStatusStringRes(header.beamStatus))
            val statusColor = context.getColor(resolveStatusColorRes(header.beamStatus))
            views.setTextViewText(R.id.lupine_status_label, statusLabel)
            views.setTextColor(R.id.lupine_status_label, statusColor)
            views.setInt(R.id.lupine_high_beam_icon, "setColorFilter", statusColor)
        }
    }

    private fun resolveBatteryColorRes(status: BatteryLevelStatus): Int = when (status) {
        BatteryLevelStatus.GOOD -> R.color.karoo_battery_good
        BatteryLevelStatus.MEDIUM -> R.color.karoo_battery_medium
        BatteryLevelStatus.LOW -> R.color.karoo_battery_low
        BatteryLevelStatus.UNKNOWN -> R.color.karoo_text_secondary
    }

    private fun resolveStatusStringRes(beamStatus: TileBeamStatus): Int = when (beamStatus) {
        TileBeamStatus.DISCONNECTED -> R.string.tile_status_disconnected
        TileBeamStatus.OFF -> R.string.tile_status_off
        TileBeamStatus.HIGH_BEAM -> R.string.tile_status_high_beam
        TileBeamStatus.DIMMED -> R.string.tile_status_dimmed
        TileBeamStatus.ACTIVE -> R.string.tile_status_active
    }

    private fun resolveStatusColorRes(beamStatus: TileBeamStatus): Int = when (beamStatus) {
        TileBeamStatus.DISCONNECTED,
        TileBeamStatus.OFF,
        -> R.color.karoo_text_secondary
        TileBeamStatus.HIGH_BEAM -> R.color.karoo_status_warn
        TileBeamStatus.DIMMED,
        TileBeamStatus.ACTIVE,
        -> R.color.karoo_status_ok
    }
}

class LupineControlRemoteViewsRenderer(
    private val remoteViewsFactory: (Context, Int) -> RemoteViews = { context, layoutId ->
        RemoteViews(context.packageName, layoutId)
    },
    private val quickDimPendingIntentFactory: (Context) -> PendingIntent = { context ->
        LupineTileActionReceiver.createQuickDimPendingIntent(context)
    },
) : LupineRemoteViewsRenderer {

    override fun render(context: Context, state: LightState, config: ViewConfig): RemoteViews {
        val model = LupineControlTileModel.from(
            state = state,
            gridSize = config.gridSize,
            isPreview = config.preview,
        )
        val layoutId = when (model.header.layoutSize) {
            TileLayoutSize.COMPACT_1X1 -> R.layout.remote_lupine_control_compact
            TileLayoutSize.WIDE_2X1 -> R.layout.remote_lupine_control_wide
            TileLayoutSize.TALL_1X2 -> R.layout.remote_lupine_control_tall
        }
        return remoteViewsFactory(context, layoutId).apply {
            RemoteViewsBindingSupport.bindHeader(
                context = context,
                views = this,
                header = model.header,
            )
            setOnClickPendingIntent(R.id.lupine_tile_root, quickDimPendingIntentFactory(context))
        }
    }
}

class LupineBrightnessBarRemoteViewsRenderer(
    private val remoteViewsFactory: (Context, Int) -> RemoteViews = { context, layoutId ->
        RemoteViews(context.packageName, layoutId)
    },
    private val stepPendingIntentFactory: (Context, BrightnessStepDirection) -> PendingIntent =
        LupineTileActionReceiver::createStepPendingIntent,
    private val setModePendingIntentFactory: (Context, LightMode) -> PendingIntent =
        LupineTileActionReceiver::createSetModePendingIntent,
) : LupineRemoteViewsRenderer {

    override fun render(context: Context, state: LightState, config: ViewConfig): RemoteViews {
        val model = LupineBrightnessBarModel.from(
            state = state,
            gridSize = config.gridSize,
            isPreview = config.preview,
        )
        val layoutId = when (model.header.layoutSize) {
            TileLayoutSize.COMPACT_1X1 -> R.layout.remote_lupine_brightness_compact
            TileLayoutSize.WIDE_2X1 -> R.layout.remote_lupine_brightness_wide
            TileLayoutSize.TALL_1X2 -> R.layout.remote_lupine_brightness_tall
        }

        return remoteViewsFactory(context, layoutId).apply {
            when (model.header.layoutSize) {
                TileLayoutSize.COMPACT_1X1 -> bindCompactStepper(context, this, model)
                TileLayoutSize.WIDE_2X1,
                TileLayoutSize.TALL_1X2,
                -> bindSegmentBar(context, this, model)
            }
        }
    }

    private fun bindCompactStepper(
        context: Context,
        views: RemoteViews,
        model: LupineBrightnessBarModel,
    ) {
        RemoteViewsBindingSupport.bindHeader(
            context = context,
            views = views,
            header = model.header,
            includeStatusAndIcon = false,
        )
        views.setProgressBar(R.id.lupine_brightness_progress, MAX_PROGRESS, model.progressPercent, false)
        views.setOnClickPendingIntent(
            R.id.lupine_btn_step_down,
            stepPendingIntentFactory(context, BrightnessStepDirection.DOWN),
        )
        views.setOnClickPendingIntent(
            R.id.lupine_btn_step_up,
            stepPendingIntentFactory(context, BrightnessStepDirection.UP),
        )
    }

    private fun bindSegmentBar(
        context: Context,
        views: RemoteViews,
        model: LupineBrightnessBarModel,
    ) {
        RemoteViewsBindingSupport.bindHeader(
            context = context,
            views = views,
            header = model.header,
            includeStatusAndIcon = true,
        )

        val activeColor = context.getColor(
            if (model.header.beamStatus == TileBeamStatus.HIGH_BEAM) {
                R.color.karoo_segment_high_beam
            } else {
                R.color.karoo_segment_active
            },
        )
        val inactiveColor = context.getColor(R.color.karoo_segment_inactive)
        val activeTextColor = context.getColor(R.color.karoo_segment_text_on_active)
        val inactiveTextColor = context.getColor(R.color.karoo_text_primary)

        SEGMENT_VIEW_IDS.zip(model.segments).forEach { (viewId, segment) ->
            views.setTextViewText(viewId, segment.shortLabel)
            views.setInt(
                viewId,
                "setBackgroundColor",
                if (segment.isFilled) activeColor else inactiveColor,
            )
            views.setTextColor(
                viewId,
                if (segment.isFilled) activeTextColor else inactiveTextColor,
            )
            views.setOnClickPendingIntent(viewId, setModePendingIntentFactory(context, segment.mode))
        }
    }

    companion object {
        private const val MAX_PROGRESS = 100
        private val SEGMENT_VIEW_IDS = listOf(
            R.id.lupine_segment_0,
            R.id.lupine_segment_1,
            R.id.lupine_segment_2,
            R.id.lupine_segment_3,
            R.id.lupine_segment_4,
        )
    }
}
