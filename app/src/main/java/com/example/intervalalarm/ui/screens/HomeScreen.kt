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
import androidx.compose.ui.res.stringResource
import com.example.intervalalarm.R
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
        val uri = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
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
                        if (cfg.isActive) stringResource(R.string.status_active) else stringResource(R.string.status_inactive),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (cfg.isActive) {
                        Text(
                            stringResource(R.string.running_range, fmtTime(cfg.startHour, cfg.startMinute), fmtTime(cfg.endHour, cfg.endMinute)),
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
                    Text(if (cfg.isActive) stringResource(R.string.btn_stop) else stringResource(R.string.btn_start))
                }
            }
        }

        // Time Window
        Text(stringResource(R.string.time_window), style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            TimePickerCard(
                label = stringResource(R.string.label_start),
                hour = cfg.startHour,
                minute = cfg.startMinute,
                enabled = !cfg.isActive,
                onPick = { h, m -> vm.updateConfig { copy(startHour = h, startMinute = m) } },
                modifier = Modifier.weight(1f)
            )
            TimePickerCard(
                label = stringResource(R.string.label_end),
                hour = cfg.endHour,
                minute = cfg.endMinute,
                enabled = !cfg.isActive,
                onPick = { h, m -> vm.updateConfig { copy(endHour = h, endMinute = m) } },
                modifier = Modifier.weight(1f)
            )
        }

        // Time Alone
        Text(stringResource(R.string.label_time_alone), style = MaterialTheme.typography.titleSmall)
        DurationPickerCard(
            seconds = cfg.timeAloneSeconds,
            enabled = !cfg.isActive,
            onPick = { vm.updateConfig { copy(timeAloneSeconds = it) } }
        )

        // Interval Range
        Text(stringResource(R.string.interval_range), style = MaterialTheme.typography.titleSmall)
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            IntervalField(
                label = stringResource(R.string.label_min),
                configValue = cfg.minIntervalMin,
                enabled = !cfg.isActive,
                onCommit = { v ->
                    vm.updateConfig { copy(minIntervalMin = v.coerceIn(1, maxIntervalMin)) }
                },
                modifier = Modifier.weight(1f)
            )
            IntervalField(
                label = stringResource(R.string.label_max),
                configValue = cfg.maxIntervalMin,
                enabled = !cfg.isActive,
                onCommit = { v ->
                    vm.updateConfig { copy(maxIntervalMin = v.coerceAtLeast(minIntervalMin)) }
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Alert Toggles
        Text(stringResource(R.string.section_alerts), style = MaterialTheme.typography.titleSmall)
        Card {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                ToggleRow(stringResource(R.string.alert_notif), cfg.notificationEnabled) {
                    vm.updateConfig { copy(notificationEnabled = it) }
                }
                HorizontalDivider()
                ToggleRow(stringResource(R.string.alert_sound), cfg.soundEnabled) {
                    vm.updateConfig { copy(soundEnabled = it) }
                }
                if (cfg.soundEnabled) {
                    val currentUri = cfg.soundUri
                    val customStr = stringResource(R.string.sound_custom)
                    val defaultStr = stringResource(R.string.sound_default)
                    val soundName = remember(currentUri) {
                        if (currentUri != null) {
                            try {
                                val r = RingtoneManager.getRingtone(ctx, currentUri.toUri())
                                r?.getTitle(ctx) ?: customStr
                            } catch (_: Exception) { customStr }
                        } else defaultStr
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
                            Text(stringResource(R.string.btn_change))
                        }
                    }
                }
                HorizontalDivider()
                ToggleRow(stringResource(R.string.alert_vibration), cfg.vibrationEnabled) {
                    vm.updateConfig { copy(vibrationEnabled = it) }
                }
            }
        }

        // Repeat Days
        Text(stringResource(R.string.section_repeat), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(R.string.repeat_hint),
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
                stringResource(R.string.warning_window_too_small),
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

@Composable
private fun DurationPickerCard(
    seconds: Long,
    enabled: Boolean,
    onPick: (Long) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60

    OutlinedCard(
        onClick = { if (enabled) showDialog = true },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("%02d:%02d:%02d".format(h, m, s), style = MaterialTheme.typography.headlineMedium)
        }
    }

    if (showDialog) {
        var localH by remember { mutableStateOf(h.toString()) }
        var localM by remember { mutableStateOf(m.toString()) }
        var localS by remember { mutableStateOf(s.toString()) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val newH = localH.toLongOrNull() ?: 0L
                    val newM = localM.toLongOrNull() ?: 0L
                    val newS = localS.toLongOrNull() ?: 0L
                    val total = (newH * 3600 + newM * 60 + newS).coerceIn(1L, 14400L)
                    onPick(total)
                    showDialog = false
                }) { Text(stringResource(R.string.dialog_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
            title = { Text(stringResource(R.string.label_time_alone)) },
            text = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = localH,
                        onValueChange = { if (it.length <= 2) localH = it },
                        label = { Text("H") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = localM,
                        onValueChange = { if (it.length <= 2) localM = it },
                        label = { Text("M") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = localS,
                        onValueChange = { if (it.length <= 2) localS = it },
                        label = { Text("S") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        )
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
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    onPick(tpState.hour, tpState.minute)
                    showDialog = false
                }) { Text(stringResource(R.string.dialog_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
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