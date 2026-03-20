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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.intervalalarm.R
import com.example.intervalalarm.ui.AlarmViewModel
import com.example.intervalalarm.service.AlarmFiringService
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AlarmViewModel) {
    val cfg by vm.config.collectAsState()
    val isFiring by AlarmFiringService.isFiring.collectAsState()
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Firing Status Card
        if (isFiring) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.notif_alarm_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(ctx, AlarmFiringService::class.java).apply {
                                    action = AlarmFiringService.ACTION_ACCEPT
                                }
                                ctx.startForegroundService(intent)
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(stringResource(R.string.btn_accept))
                        }
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(ctx, AlarmFiringService::class.java).apply {
                                    action = AlarmFiringService.ACTION_DISMISS
                                }
                                ctx.startForegroundService(intent)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(stringResource(R.string.btn_dismiss))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Status Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (cfg.isActive)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        if (cfg.isActive) stringResource(R.string.status_active) else stringResource(R.string.status_inactive),
                        style = MaterialTheme.typography.titleSmall
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
                    ) else ButtonDefaults.filledTonalButtonColors(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(if (cfg.isActive) stringResource(R.string.btn_stop) else stringResource(R.string.btn_start))
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.5f, fill = false).heightIn(min = 4.dp))

        // Time Window
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
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

        Spacer(modifier = Modifier.weight(0.5f, fill = false).heightIn(min = 4.dp))

        // Time Alone & Adjustment
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DurationPickerCard(
                seconds = cfg.timeAloneSeconds,
                enabled = !cfg.isActive,
                onPick = { vm.updateConfig { copy(timeAloneSeconds = it) } },
                modifier = Modifier.weight(1.5f)
            )
            IntervalField(
                label = "Adj %",
                configValue = cfg.adjustmentPercent,
                enabled = !cfg.isActive,
                onCommit = { v ->
                    vm.updateConfig { copy(adjustmentPercent = v.coerceIn(1, 100)) }
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.weight(0.5f, fill = false).heightIn(min = 4.dp))

        // Interval Range
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
        ) {
            IntervalField(
                label = "Min Int",
                configValue = cfg.minIntervalMin,
                enabled = !cfg.isActive,
                onCommit = { v ->
                    vm.updateConfig { copy(minIntervalMin = v.coerceIn(1, cfg.maxIntervalMin)) }
                },
                modifier = Modifier.weight(1f)
            )
            IntervalField(
                label = "Max Int",
                configValue = cfg.maxIntervalMin,
                enabled = !cfg.isActive,
                onCommit = { v ->
                    vm.updateConfig { copy(maxIntervalMin = v.coerceAtLeast(cfg.minIntervalMin)) }
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.weight(0.5f, fill = false).heightIn(min = 4.dp))

        // Alert Toggles
        Card(modifier = Modifier.weight(1f, fill = false)) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
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
                            .padding(start = 8.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(soundName, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        TextButton(
                            onClick = {
                                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    if (currentUri != null) {
                                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri.toUri())
                                    }
                                }
                                ringtoneLauncher.launch(intent)
                            },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(stringResource(R.string.btn_change), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                ToggleRow(stringResource(R.string.alert_vibration), cfg.vibrationEnabled) {
                    vm.updateConfig { copy(vibrationEnabled = it) }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.5f, fill = false).heightIn(min = 4.dp))

        // Repeat Days
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
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
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.extraSmall
                )
            }
        }

        // Validation hint
        if (cfg.windowMinutes < cfg.minIntervalMin) {
            Text(
                stringResource(R.string.warning_window_too_small),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

// ── Helpers ──────────────────────────────────────────────

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked, 
            onCheckedChange = onToggle,
            modifier = Modifier.scale(0.7f)
        )
    }
}

@Composable
private fun DurationPickerCard(
    seconds: Long,
    enabled: Boolean,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60

    OutlinedCard(
        onClick = { if (enabled) showDialog = true },
        modifier = modifier,
        enabled = enabled
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("%02d:%02d:%02d".format(h, m, s), style = MaterialTheme.typography.titleLarge)
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                fmtTime(hour, minute),
                style = MaterialTheme.typography.titleLarge
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
    var text by remember(configValue) { mutableStateOf(configValue.toString()) }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            if (newText.isEmpty() || newText.all { it.isDigit() }) {
                text = newText
                val parsed = newText.toIntOrNull()
                if (parsed != null && parsed > 0) {
                    onCommit(parsed)
                }
            }
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

private fun fmtTime(h: Int, m: Int): String =
    "%02d:%02d".format(h, m)
