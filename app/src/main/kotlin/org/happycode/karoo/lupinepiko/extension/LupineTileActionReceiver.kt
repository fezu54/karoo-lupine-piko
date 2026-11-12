package org.happycode.karoo.lupinepiko.extension

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.happycode.karoo.lupinepiko.application.BrightnessStepDirection
import org.happycode.karoo.lupinepiko.application.QuickDimToggleUseCase
import org.happycode.karoo.lupinepiko.application.SetLightModeUseCase
import org.happycode.karoo.lupinepiko.application.StepBrightnessUseCase
import org.happycode.karoo.lupinepiko.domain.LightMode

class LupineTileActionReceiver(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val quickDimToggleUseCase: QuickDimToggleUseCase = LupineServiceRegistry.quickDimToggle,
    private val stepBrightnessUseCase: StepBrightnessUseCase = LupineServiceRegistry.stepBrightness,
    private val setLightModeUseCase: SetLightModeUseCase = LupineServiceRegistry.setLightMode,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val modeName = intent.getStringExtra(EXTRA_LIGHT_MODE)
        if (!isSupportedAction(action)) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                dispatchAction(action = action, modeName = modeName)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    private suspend fun dispatchAction(action: String, modeName: String?) {
        when (action) {
            ACTION_QUICK_DIM_TOGGLE -> quickDimToggleUseCase()
            ACTION_STEP_BRIGHTNESS_UP -> stepBrightnessUseCase(BrightnessStepDirection.UP)
            ACTION_STEP_BRIGHTNESS_DOWN -> stepBrightnessUseCase(BrightnessStepDirection.DOWN)
            ACTION_SET_LIGHT_MODE -> {
                val targetMode = modeName?.let { name -> LightMode.entries.firstOrNull { it.name == name } }
                targetMode?.let { setLightModeUseCase(it) }
            }
        }
    }

    companion object {
        const val ACTION_QUICK_DIM_TOGGLE =
            "org.happycode.karoo.lupinepiko.action.QUICK_DIM_TOGGLE"
        const val ACTION_STEP_BRIGHTNESS_UP =
            "org.happycode.karoo.lupinepiko.action.STEP_BRIGHTNESS_UP"
        const val ACTION_STEP_BRIGHTNESS_DOWN =
            "org.happycode.karoo.lupinepiko.action.STEP_BRIGHTNESS_DOWN"
        const val ACTION_SET_LIGHT_MODE =
            "org.happycode.karoo.lupinepiko.action.SET_LIGHT_MODE"
        const val EXTRA_LIGHT_MODE = "extra_light_mode"

        private const val REQUEST_CODE_QUICK_DIM = 1001
        private const val REQUEST_CODE_STEP_DOWN = 1002
        private const val REQUEST_CODE_STEP_UP = 1003
        private const val REQUEST_CODE_SET_MODE_BASE = 1010

        private fun isSupportedAction(action: String): Boolean = action in setOf(
            ACTION_QUICK_DIM_TOGGLE,
            ACTION_STEP_BRIGHTNESS_UP,
            ACTION_STEP_BRIGHTNESS_DOWN,
            ACTION_SET_LIGHT_MODE,
        )

        fun createQuickDimPendingIntent(context: Context): PendingIntent =
            createPendingIntent(
                context = context,
                action = ACTION_QUICK_DIM_TOGGLE,
                requestCode = REQUEST_CODE_QUICK_DIM,
            )

        fun createStepPendingIntent(context: Context, direction: BrightnessStepDirection): PendingIntent =
            when (direction) {
                BrightnessStepDirection.DOWN -> createPendingIntent(
                    context = context,
                    action = ACTION_STEP_BRIGHTNESS_DOWN,
                    requestCode = REQUEST_CODE_STEP_DOWN,
                )
                BrightnessStepDirection.UP -> createPendingIntent(
                    context = context,
                    action = ACTION_STEP_BRIGHTNESS_UP,
                    requestCode = REQUEST_CODE_STEP_UP,
                )
            }

        fun createSetModePendingIntent(context: Context, mode: LightMode): PendingIntent =
            createPendingIntent(
                context = context,
                action = ACTION_SET_LIGHT_MODE,
                requestCode = REQUEST_CODE_SET_MODE_BASE + mode.ordinal,
                mode = mode,
            )

        private fun createPendingIntent(
            context: Context,
            action: String,
            requestCode: Int,
            mode: LightMode? = null,
        ): PendingIntent {
            val intent = Intent(context, LupineTileActionReceiver::class.java).apply {
                this.action = action
                mode?.let { putExtra(EXTRA_LIGHT_MODE, it.name) }
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
