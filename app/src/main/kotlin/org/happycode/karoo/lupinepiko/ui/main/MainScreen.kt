package org.happycode.karoo.lupinepiko.ui.main

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlinx.coroutines.launch
import org.happycode.karoo.lupinepiko.R
import org.happycode.karoo.lupinepiko.domain.LightMode
import org.happycode.karoo.lupinepiko.domain.LightState
import org.happycode.karoo.lupinepiko.extension.LupineServiceRegistry
import org.happycode.karoo.lupinepiko.theme.lupinePikoColors

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val karooSystem = remember { KarooSystemService(context) }
    val coroutineScope = rememberCoroutineScope()
    var connected by remember { mutableStateOf(false) }
    val lightState by LupineServiceRegistry.gateway.state.collectAsState()

    DisposableEffect(Unit) {
        karooSystem.connect { isConnected ->
            connected = isConnected
        }
        onDispose {
            karooSystem.disconnect()
        }
    }

    MainScreenContent(
        connected = connected,
        lightState = lightState,
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
    )
}

@Composable
fun MainScreenContent(
    connected: Boolean,
    lightState: LightState = LightState(),
    onQuickDimToggle: () -> Unit = {},
    onCycleMode: () -> Unit = {},
    onTogglePower: () -> Unit = {},
    onSelectMode: (LightMode) -> Unit = {},
) {
    val colors = MaterialTheme.lupinePikoColors
    val isCurrentlyBright = lightState.currentMode.isBright

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
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(
                    if (connected) R.string.status_connected else R.string.status_disconnected
                ),
                style = MaterialTheme.typography.bodyMedium,
            )

            lightState.telemetry?.let { telemetry ->
                Text(
                    text = stringResource(
                        R.string.telemetry_format,
                        telemetry.batteryVoltage,
                        telemetry.ledCurrentAmps,
                        telemetry.temperatureCelsius,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Button(
                onClick = onQuickDimToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrentlyBright) colors.warning else colors.success,
                ),
            ) {
                Text(
                    text = stringResource(
                        if (isCurrentlyBright) {
                            R.string.quick_dim_action_dim
                        } else {
                            R.string.quick_dim_action_restore
                        }
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

            Text(
                text = stringResource(R.string.section_modes),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 4.dp),
            )

            LightMode.entries.forEach { mode ->
                val isSelected = lightState.currentMode == mode
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
