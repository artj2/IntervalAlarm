package com.example.intervalalarm.ui.screens

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.intervalalarm.ui.AlarmViewModel
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AlarmViewModel) {
    val cfg by vm.config.collectAsState()
    val scroll = rememberScrollState()
    val ctx = LocalContext.current

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        @Suppress("DEPRECATION")
        val uri = result.data
            ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        vm.updateConfig { copy(soundUri = uri?.toString()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (cfg.isActive)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (cfg.isActive) "Alarm Active" else "Alarm Inactive",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (cfg.isActive) {
                        Text(
                            "Running from ${fmtTime(cfg.startHour, cfg.startMinute)} to ${fmtTime(cfg.endHour, cfg.endMinute)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                FilledTonalButton(
                    onClick = { vm.toggleActive() },
                    colors = if (cfg.isActive) ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ) else ButtonDefaults.filledTonalButtonColors()
                ) {
                    Text(if (cfg.isActive) "Stop" else "Start")
                }
            }
        }

        // Time Window
        Text("Time Window", style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TimePickerCard(
                label = "Start",
                hour = cfg.startHour,
                minute = cfg.startMinute,
                enabled = !cfg.isActive,
                onPick = { h, m -> vm.updateConfig { copy(startHour = h, startMinute = m) } },
                modifier = Modifier.weight(1f)
            )
            TimePickerCard(
                label = "End",
                hour = cfg.endHour,
                minute = cfg.endMinute,
                enabled = !cfg.isActive,
                onPick = { h, m -> vm.updateConfig { copy(endHour = h, endMinute = m) } },
                modifier = Modifier.weight(1f)
            )
        }

        // Interval Range
        Text("Interval Range (minutes)", style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            IntervalField(
                label = "Min",
                configValue = cfg.minIntervalMin,
                enabled = !cfg.isActive,
                onCommit = { v ->
                    vm.updateConfig { copy(minIntervalMin = v.coerceIn(1, maxIntervalMin)) }
                },
                modifier = Modifier.weight(1f)
            )
            IntervalField(
                label = "Max",
                configValue = cfg.maxIntervalMin,
                enabled = !cfg.isActive,
                onCommit = { v ->
                    vm.updateConfig { copy(maxIntervalMin = v.coerceAtLeast(minIntervalMin)) }
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Alert Toggles
        Text("Alerts", style = MaterialTheme.typography.titleSmall)
        Card {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                ToggleRow("Notification", cfg.notificationEnabled) {
                    vm.updateConfig { copy(notificationEnabled = it) }
                }
                HorizontalDivider()
                ToggleRow("Sound", cfg.soundEnabled) {
                    vm.updateConfig { copy(soundEnabled = it) }
                }
                if (cfg.soundEnabled) {
                    val currentUri = cfg.soundUri
                    val soundName = remember(currentUri) {
                        if (currentUri != null) {
                            try {
                                val r = RingtoneManager.getRingtone(ctx, currentUri.toUri())
                                r?.getTitle(ctx) ?: "Custom"
                            } catch (_: Exception) { "Custom" }
                        } else "Default alarm tone"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(soundName, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = {
                            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                if (currentUri != null) {
                                    putExtra(
                                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                        currentUri.toUri()
                                    )
                                }
                            }
                            ringtoneLauncher.launch(intent)
                        }) {
                            Text("Change")
                        }
                    }
                }
                HorizontalDivider()
                ToggleRow("Vibration", cfg.vibrationEnabled) {
                    vm.updateConfig { copy(vibrationEnabled = it) }
                }
            }
        }

        // Repeat Days
        Text("Repeat Days", style = MaterialTheme.typography.titleSmall)
        Text(
            "Leave all unselected for manual start/stop only",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            for (dow in DayOfWeek.entries) {
                val selected = cfg.repeatDays.contains(dow.value)
                FilterChip(
                    selected = selected,
                    enabled = !cfg.isActive,
                    onClick = {
                        val newDays = if (selected)
                            cfg.repeatDays - dow.value
                        else
                            cfg.repeatDays + dow.value
                        vm.updateConfig { copy(repeatDays = newDays) }
                    },
                    label = {
                        Text(
                            dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Validation hint
        if (cfg.windowMinutes < cfg.minIntervalMin) {
            Text(
                "Warning: minimum interval is longer than the time window",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ── Helpers ──────────────────────────────────────────────

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerCard(
    label: String,
    hour: Int,
    minute: Int,
    enabled: Boolean,
    onPick: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val tpState = rememberTimePickerState(initialHour = hour, initialMinute = minute)

    OutlinedCard(
        onClick = { if (enabled) showDialog = true },
        modifier = modifier,
        enabled = enabled
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                fmtTime(hour, minute),
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }

    if (showDialog) {
        fun dismiss() { showDialog = false }
        AlertDialog(
            onDismissRequest = { dismiss() },
            confirmButton = {
                TextButton(onClick = {
                    onPick(tpState.hour, tpState.minute)
                    dismiss()
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { dismiss() }) { Text("Cancel") }
            },
            text = { TimePicker(state = tpState) }
        )
    }
}

@Composable
private fun IntervalField(
    label: String,
    configValue: Int,
    enabled: Boolean,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Local text state decoupled from the config — only syncs on focus loss
    var text by remember(configValue) { mutableStateOf(configValue.toString()) }
    var hasFocus by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            // Allow empty string and digits only
            if (newText.isEmpty() || newText.all { it.isDigit() }) {
                text = newText
            }
        },
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
            .onFocusChanged { focusState ->
                if (hasFocus && !focusState.isFocused) {
                    // Lost focus — commit the value
                    val parsed = text.toIntOrNull()
                    if (parsed != null && parsed > 0) {
                        onCommit(parsed)
                    } else {
                        // Reset to current config value if invalid/empty
                        text = configValue.toString()
                    }
                }
                hasFocus = focusState.isFocused
            }
    )
}

private fun fmtTime(h: Int, m: Int): String =
    "%02d:%02d".format(h, m)