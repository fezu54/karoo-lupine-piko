package org.happycode.karoo.lupinepiko.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.SavedDevices
import kotlinx.coroutines.launch
import org.happycode.karoo.lupinepiko.R
import org.happycode.karoo.lupinepiko.domain.BatteryCapacity
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState
import org.happycode.karoo.lupinepiko.domain.QuickDimConfig
import org.happycode.karoo.lupinepiko.extension.LupineServiceRegistry
import org.happycode.karoo.lupinepiko.extension.PairedLampAddress
import org.happycode.karoo.lupinepiko.theme.lupinePikoColors

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val karooSystem = remember { KarooSystemService(context) }
    val coroutineScope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(false) }
    var pairedAddress by remember { mutableStateOf<String?>(null) }
    val lightState by LupineServiceRegistry.gateway.state.collectAsState()
    val config by LupineServiceRegistry.configRepository.config.collectAsState()

    DisposableEffect(Unit) {
        var savedDevicesConsumerId: String? = null
        karooSystem.connect { isConnected ->
            connected = isConnected
            if (isConnected && savedDevicesConsumerId == null) {
                savedDevicesConsumerId = karooSystem.addConsumer<SavedDevices> { saved ->
                    pairedAddress = PairedLampAddress.from(saved.devices.map { it.id })
                }
            }
        }
        onDispose {
            savedDevicesConsumerId?.let { karooSystem.removeConsumer(it) }
            karooSystem.disconnect()
        }
    }

    MainScreenContent(
        connected,
        lightState,
        config,
        pairedAddress,
        onQuickDimToggle = {
            coroutineScope.launch { LupineServiceRegistry.quickDimToggle() }
        },
        onCycleMode = {
            coroutineScope.launch { LupineServiceRegistry.cycleLightMode() }
        },
        onTogglePower = {
            coroutineScope.launch { LupineServiceRegistry.togglePower() }
        },
        onSelectMode = { mode ->
            coroutineScope.launch { LupineServiceRegistry.setLightMode(mode) }
        },
        onSelectHighBeamPreset = LupineServiceRegistry.configRepository::updateHighBeamMode,
        onSelectDimmedPreset = LupineServiceRegistry.configRepository::updateDimmedMode,
        onToggleCycleMode = LupineServiceRegistry.configRepository::toggleCycleMode,
        onSelectBatteryCapacity = LupineServiceRegistry.configRepository::updateBatteryCapacity,
    )
}

@Composable
fun MainScreenContent(
    connected: Boolean,
    lightState: LightState = LightState(),
    config: QuickDimConfig = QuickDimConfig(),
    pairedAddress: String? = null,
    onQuickDimToggle: () -> Unit = {},
    onCycleMode: () -> Unit = {},
    onTogglePower: () -> Unit = {},
    onSelectMode: (LightMode) -> Unit = {},
    onSelectHighBeamPreset: (LightMode) -> Unit = {},
    onSelectDimmedPreset: (LightMode) -> Unit = {},
    onToggleCycleMode: (LightMode) -> Unit = {},
    onSelectBatteryCapacity: (BatteryCapacity) -> Unit = {},
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CockpitHeader(
                connected,
                lightState,
                batteryCapacity = config.batteryCapacity,
            )

            QuickControlSection(
                isCurrentlyBright = lightState.currentMode.isBright,
                onQuickDimToggle,
                onCycleMode,
                onTogglePower,
            )

            PairingCard(pairedAddress)

            PresetSettingsCard(
                config,
                onSelectHighBeamPreset,
                onSelectDimmedPreset,
                onToggleCycleMode,
                onSelectBatteryCapacity,
            )

            ModeSelectionSection(
                currentMode = lightState.currentMode,
                onSelectMode,
            )
        }
    }
}

@Composable
private fun CockpitHeader(
    connected: Boolean,
    lightState: LightState,
    batteryCapacity: BatteryCapacity,
) {
    Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        text = stringResource(
            if (connected) R.string.status_connected else R.string.status_disconnected,
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    lightState.telemetry?.let { telemetry ->
        Text(
            text = stringResource(
                R.string.telemetry_format,
                telemetry.batteryVoltage,
                telemetry.remainingBatteryPercent(batteryCapacity),
                telemetry.ledCurrentAmps,
                telemetry.temperatureCelsius,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun QuickControlSection(
    isCurrentlyBright: Boolean,
    onQuickDimToggle: () -> Unit,
    onCycleMode: () -> Unit,
    onTogglePower: () -> Unit,
) {
    val colors = MaterialTheme.lupinePikoColors

    Button(
        onClick = onQuickDimToggle,
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isCurrentlyBright) colors.statusWarning else colors.statusGood,
        ),
    ) {
        Text(
            text = stringResource(
                if (isCurrentlyBright) R.string.quick_dim_action_dim else R.string.quick_dim_action_restore,
            ),
            style = MaterialTheme.typography.titleMedium,
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onCycleMode,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
        ) {
            Text(text = stringResource(R.string.action_cycle_mode))
        }
        OutlinedButton(
            onClick = onTogglePower,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
        ) {
            Text(text = stringResource(R.string.action_power_toggle))
        }
    }
}

@Composable
private fun ModeSelectionSection(
    currentMode: LightMode,
    onSelectMode: (LightMode) -> Unit,
) {
    Text(
        text = stringResource(R.string.section_modes),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 4.dp),
    )
    LightMode.entries.forEach { mode ->
        val isSelected = currentMode == mode
        Button(
            onClick = { onSelectMode(mode) },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Text(text = stringResource(mode.labelResId()))
        }
    }
}

@Composable
private fun PresetSettingsCard(
    config: QuickDimConfig,
    onSelectHighBeamPreset: (LightMode) -> Unit,
    onSelectDimmedPreset: (LightMode) -> Unit,
    onToggleCycleMode: (LightMode) -> Unit,
    onSelectBatteryCapacity: (BatteryCapacity) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.section_settings),
                style = MaterialTheme.typography.titleMedium,
            )

            PresetDropdown(
                label = stringResource(R.string.setting_high_beam_preset),
                selectedLabel = stringResource(config.highBeamMode.labelResId()),
                options = LightMode.highBeamPresets,
                optionLabel = { stringResource(it.labelResId()) },
                onOptionSelected = onSelectHighBeamPreset,
            )

            PresetDropdown(
                label = stringResource(R.string.setting_dimmed_preset),
                selectedLabel = stringResource(config.dimmedMode.labelResId()),
                options = LightMode.dimmedPresets,
                optionLabel = { stringResource(it.labelResId()) },
                onOptionSelected = onSelectDimmedPreset,
            )

            PresetDropdown(
                label = stringResource(R.string.setting_battery_capacity),
                selectedLabel = stringResource(config.batteryCapacity.labelResId()),
                options = BatteryCapacity.entries,
                optionLabel = { stringResource(it.labelResId()) },
                onOptionSelected = onSelectBatteryCapacity,
            )

            CycleModeChecklist(
                selectedModes = config.cycleModes,
                onToggleCycleMode,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PresetDropdown(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onOptionSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(text = label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = optionLabel(option)) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

@Composable
private fun CycleModeChecklist(
    selectedModes: List<LightMode>,
    onToggleCycleMode: (LightMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.setting_cycle_modes),
            style = MaterialTheme.typography.titleSmall,
        )
        LightMode.cyclableModes.forEach { mode ->
            val checked = mode in selectedModes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable { onToggleCycleMode(mode) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked,
                    onCheckedChange = null,
                )
                Text(
                    text = stringResource(mode.labelResId()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PairingCard(pairedAddress: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.section_pairing),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = pairedAddress
                    ?.let { stringResource(R.string.paired_lamp_format, it) }
                    ?: stringResource(R.string.no_lamp_paired),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (pairedAddress == null) {
                Text(
                    text = stringResource(R.string.pairing_instructions),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@StringRes
fun LightMode.labelResId(): Int = when (this) {
    LightMode.OFF -> R.string.mode_off
    LightMode.DIMMED -> R.string.mode_dimmed
    LightMode.DIFFUSE -> R.string.mode_diffuse
    LightMode.LOW -> R.string.mode_low
    LightMode.MEDIUM -> R.string.mode_medium
    LightMode.HIGH -> R.string.mode_high
    LightMode.MAX -> R.string.mode_max
}

@StringRes
fun BatteryCapacity.labelResId(): Int = when (this) {
    BatteryCapacity.AH_2_0 -> R.string.battery_capacity_2_0_ah
    BatteryCapacity.AH_3_5 -> R.string.battery_capacity_3_5_ah
    BatteryCapacity.AH_5_0 -> R.string.battery_capacity_5_0_ah
    BatteryCapacity.AH_6_9 -> R.string.battery_capacity_6_9_ah
}
