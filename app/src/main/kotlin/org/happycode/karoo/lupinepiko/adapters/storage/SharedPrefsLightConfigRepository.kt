package org.happycode.karoo.lupinepiko.adapters.storage

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.happycode.karoo.lupinepiko.application.LightConfigRepository
import org.happycode.karoo.lupinepiko.domain.BatteryCapacity
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.QuickDimConfig

class SharedPrefsLightConfigRepository(
    private var preferences: SharedPreferences? = null,
    initialConfig: QuickDimConfig = QuickDimConfig(),
) : LightConfigRepository {

    private val _config = MutableStateFlow(loadFromPrefs(initialConfig))
    override val config: StateFlow<QuickDimConfig> = _config.asStateFlow()

    fun attachPreferences(sharedPreferences: SharedPreferences) {
        if (preferences == null) {
            preferences = sharedPreferences
            _config.value = loadFromPrefs(_config.value)
        }
    }

    override fun updateHighBeamMode(mode: LightMode) {
        if (mode !in LightMode.highBeamPresets) return
        _config.update { current ->
            current.copy(highBeamMode = mode).also { saveToPrefs(it) }
        }
    }

    override fun updateDimmedMode(mode: LightMode) {
        if (mode !in LightMode.dimmedPresets) return
        _config.update { current ->
            current.copy(dimmedMode = mode).also { saveToPrefs(it) }
        }
    }

    override fun updateCycleModes(modes: List<LightMode>) {
        val sanitized = LightMode.cyclableModes
            .filter { it in modes }
            .ifEmpty { LightMode.defaultCycleModes }
        _config.update { current ->
            current.copy(cycleModes = sanitized).also { saveToPrefs(it) }
        }
    }

    override fun toggleCycleMode(mode: LightMode) {
        _config.update { current ->
            current.withToggledCycleMode(mode).also { saveToPrefs(it) }
        }
    }

    override fun updateBatteryCapacity(capacity: BatteryCapacity) {
        _config.update { current ->
            current.copy(batteryCapacity = capacity).also { saveToPrefs(it) }
        }
    }

    private fun loadFromPrefs(fallback: QuickDimConfig): QuickDimConfig =
        preferences?.let { prefs ->
            val highBeam = prefs.getString(KEY_HIGH_BEAM_MODE, null)
                ?.let { parseMode(it) }
                ?.takeIf { it in LightMode.highBeamPresets }
                ?: fallback.highBeamMode
            val dimmed = prefs.getString(KEY_DIMMED_MODE, null)
                ?.let { parseMode(it) }
                ?.takeIf { it in LightMode.dimmedPresets }
                ?: fallback.dimmedMode
            val cycleModes = prefs.getString(KEY_CYCLE_MODES, null)
                ?.split(",")
                ?.mapNotNull { parseMode(it.trim()) }
                ?.let { parsed -> LightMode.cyclableModes.filter { it in parsed } }
                ?.takeIf { it.isNotEmpty() }
                ?: fallback.cycleModes
            val batteryCapacity = prefs.getString(KEY_BATTERY_CAPACITY, null)
                ?.let { parseBatteryCapacity(it) }
                ?: fallback.batteryCapacity
            QuickDimConfig(
                highBeamMode = highBeam,
                dimmedMode = dimmed,
                cycleModes,
                batteryCapacity,
            )
        } ?: fallback

    private fun saveToPrefs(config: QuickDimConfig) {
        preferences?.edit {
            putString(KEY_HIGH_BEAM_MODE, config.highBeamMode.name)
            putString(KEY_DIMMED_MODE, config.dimmedMode.name)
            putString(KEY_CYCLE_MODES, config.cycleModes.joinToString(",") { it.name })
            putString(KEY_BATTERY_CAPACITY, config.batteryCapacity.name)
        }
    }

    private fun parseMode(name: String): LightMode? =
        LightMode.entries.firstOrNull { it.name == name }

    private fun parseBatteryCapacity(name: String): BatteryCapacity? =
        BatteryCapacity.entries.firstOrNull { it.name == name }

    companion object {
        const val PREFS_NAME = "lupine_piko_prefs"
        const val KEY_HIGH_BEAM_MODE = "pref_high_beam_mode"
        const val KEY_DIMMED_MODE = "pref_dimmed_mode"
        const val KEY_CYCLE_MODES = "pref_enabled_cycle_modes"
        const val KEY_BATTERY_CAPACITY = "pref_battery_capacity"
    }
}
