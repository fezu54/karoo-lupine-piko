package org.happycode.karoo.lupinepiko.extension

import android.content.Context
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.happycode.karoo.lupinepiko.application.LupineBleGateway

class LupineGraphicalDataType(
    extension: String,
    typeId: String,
    private val gateway: LupineBleGateway,
    private val scope: CoroutineScope,
    private val renderer: LupineRemoteViewsRenderer,
    private val minUpdateIntervalMs: Long = DEFAULT_MIN_UPDATE_INTERVAL_MS,
    private val clockMs: () -> Long = System::currentTimeMillis,
) : DataTypeImpl(extension = extension, typeId = typeId) {

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = Job(scope.coroutineContext[Job])
        val streamScope = CoroutineScope(scope.coroutineContext + job)

        streamScope.launch {
            gateway.state.collect { lightState ->
                val streamState = if (lightState.isConnected) {
                    StreamState.Streaming(
                        DataPoint(
                            dataTypeId = dataTypeId,
                            values = mapOf(DataType.Field.SINGLE to lightState.currentMode.watts),
                        ),
                    )
                } else {
                    StreamState.Searching
                }
                emitter.onNext(streamState)
            }
        }

        emitter.setCancellable { job.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateGraphicConfig(showHeader = false))

        val job = Job(scope.coroutineContext[Job])
        val viewScope = CoroutineScope(scope.coroutineContext + job)

        viewScope.launch {
            var lastEmittedAtMs: Long? = null
            gateway.state.collectLatest { lightState ->
                if (minUpdateIntervalMs > 0L) {
                    lastEmittedAtMs?.let { lastAt ->
                        val remainingMs = minUpdateIntervalMs - (clockMs() - lastAt)
                        if (remainingMs > 0L) {
                            delay(remainingMs)
                        }
                    }
                }
                emitter.updateView(renderer.render(context, lightState, config))
                lastEmittedAtMs = clockMs()
            }
        }

        emitter.setCancellable { job.cancel() }
    }

    companion object {
        const val TYPE_LIGHT_CONTROL = "lupine-light-control"
        const val TYPE_BRIGHTNESS_BAR = "lupine-brightness-bar"
        const val DEFAULT_MIN_UPDATE_INTERVAL_MS = 950L
    }
}
