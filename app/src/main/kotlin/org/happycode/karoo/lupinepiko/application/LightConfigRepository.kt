package org.happycode.karoo.lupinepiko.application

import kotlinx.coroutines.flow.StateFlow
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.QuickDimConfig

interface LightConfigRepository {
    val config: StateFlow<QuickDimConfig>

    fun updateHighBeamMode(mode: LightMode)

    fun updateDimmedMode(mode: LightMode)

    fun updateCycleModes(modes: List<LightMode>)
}
