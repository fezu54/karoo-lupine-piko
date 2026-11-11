package org.happycode.karoo.lupinepiko.adapters.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.happycode.karoo.lupinepiko.application.LightConfigRepository
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.QuickDimConfig

class SharedPrefsLightConfigRepository(
    private val preferences: SharedPreferences? = null,
    initialConfig: QuickDimConfig = QuickDimConfig(),
) : LightConfigRepository {

    private val _config = MutableStateFlow(loadFromPrefs(initialConfig))
    override val config: StateFlow<QuickDimConfig> = _config.asStateFlow()

    override fun updateHighBeamMode(mode: LightMode) {
        _config.update { current ->
            current.copy(highBeamMode = mode).also { saveToPrefs(it) }
        }
    }

    override fun updateDimmedMode(mode: LightMode) {
        _config.update { current ->
            current.copy(dimmedMode = mode).also { saveToPrefs(it) }
        }
    }

    override fun updateCycleModes(modes: List<LightMode>) {
        val sanitized = modes.ifEmpty { LightMode.defaultCycleModes }
        _config.update { current ->
            current.copy(cycleModes = sanitized).also { saveToPrefs(it) }
        }
    }

    private fun loadFromPrefs(fallback: QuickDimConfig): QuickDimConfig =
        preferences?.let { prefs ->
            val highBeam = prefs.getString(KEY_HIGH_BEAM_MODE, null)
                ?.let { parseMode(it) }
                ?: fallback.highBeamMode
            val dimmed = prefs.getString(KEY_DIMMED_MODE, null)
                ?.let { parseMode(it) }
                ?: fallback.dimmedMode
            val cycleModes = prefs.getString(KEY_CYCLE_MODES, null)
                ?.split(",")
                ?.mapNotNull { parseMode(it.trim()) }
                ?.takeIf { it.isNotEmpty() }
                ?: fallback.cycleModes
            QuickDimConfig(
                highBeamMode = highBeam,
                dimmedMode = dimmed,
                cycleModes = cycleModes,
            )
        } ?: fallback

    private fun saveToPrefs(config: QuickDimConfig) {
        preferences?.edit {
            putString(KEY_HIGH_BEAM_MODE, config.highBeamMode.name)
            putString(KEY_DIMMED_MODE, config.dimmedMode.name)
            putString(KEY_CYCLE_MODES, config.cycleModes.joinToString(",") { it.name })
        }
    }

    private fun parseMode(name: String): LightMode? =
        LightMode.entries.firstOrNull { it.name == name }

    companion object {
        const val PREFS_NAME = "lupine_piko_prefs"
        const val KEY_HIGH_BEAM_MODE = "pref_high_beam_mode"
        const val KEY_DIMMED_MODE = "pref_dimmed_mode"
        const val KEY_CYCLE_MODES = "pref_enabled_cycle_modes"
    }
}
