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

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val scaleFactor = (screenHeight / 700.dp).coerceIn(0.8f, 1.5f)
        val labelStyle = MaterialTheme.typography.labelMedium.copy(fontSize = (MaterialTheme.typography.labelMedium.fontSize * scaleFactor))
        val bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = (MaterialTheme.typography.bodyMedium.fontSize * scaleFactor))
        val titleStyle = MaterialTheme.typography.titleMedium.copy(fontSize = (MaterialTheme.typography.titleMedium.fontSize * scaleFactor))
        val largeStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = (MaterialTheme.typography.headlineMedium.fontSize * scaleFactor))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Firing Status Card
            if (isFiring) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.notif_alarm_title), style = titleStyle)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = {
                                    val intent = Intent(ctx, AlarmFiringService::class.java).apply { action = AlarmFiringService.ACTION_ACCEPT }
                                    ctx.startForegroundService(intent)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.btn_accept), fontSize = bodyStyle.fontSize) }
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(ctx, AlarmFiringService::class.java).apply { action = AlarmFiringService.ACTION_DISMISS }
                                    ctx.startForegroundService(intent)
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text(stringResource(R.string.btn_dismiss), fontSize = bodyStyle.fontSize) }
                        }
                    }
                }
            }

            // Status Section
            Column(modifier = Modifier.weight(1.2f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (cfg.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(if (cfg.isActive) stringResource(R.string.status_active) else stringResource(R.string.status_inactive), style = titleStyle)
                            if (cfg.isActive) {
                                Text(stringResource(R.string.running_range, fmtTime(cfg.startHour, cfg.startMinute), fmtTime(cfg.endHour, cfg.endMinute)), style = bodyStyle)
                            }
                        }
                        FilledTonalButton(
                            onClick = { vm.toggleActive() },
                            modifier = Modifier.height(48.dp * scaleFactor)
                        ) { Text(if (cfg.isActive) stringResource(R.string.btn_stop) else stringResource(R.string.btn_start), fontSize = bodyStyle.fontSize) }
                    }
                }
            }

            // Time Window Section
            Column(modifier = Modifier.weight(1.5f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.time_window), style = labelStyle)
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimePickerCard(
                        label = stringResource(R.string.label_start),
                        hour = cfg.startHour, minute = cfg.startMinute,
                        enabled = !cfg.isActive, textStyle = largeStyle, labelStyle = labelStyle,
                        onPick = { h, m -> vm.updateConfig { copy(startHour = h, startMinute = m) } },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    TimePickerCard(
                        label = stringResource(R.string.label_end),
                        hour = cfg.endHour, minute = cfg.endMinute,
                        enabled = !cfg.isActive, textStyle = largeStyle, labelStyle = labelStyle,
                        onPick = { h, m -> vm.updateConfig { copy(endHour = h, endMinute = m) } },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            // Time Alone Section
            Column(modifier = Modifier.weight(1.5f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.label_time_alone), style = labelStyle, modifier = Modifier.weight(1.5f))
                    Text(stringResource(R.string.label_adjustment), style = labelStyle, modifier = Modifier.weight(1f))
                }
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DurationPickerCard(
                        seconds = cfg.timeAloneSeconds, enabled = !cfg.isActive, textStyle = largeStyle,
                        onPick = { vm.updateConfig { copy(timeAloneSeconds = it) } },
                        modifier = Modifier.weight(1.5f).fillMaxHeight()
                    )
                    IntervalField(
                        label = "%", configValue = cfg.adjustmentPercent, enabled = !cfg.isActive, textStyle = bodyStyle, labelStyle = labelStyle,
                        onCommit = { v -> vm.updateConfig { copy(adjustmentPercent = v.coerceIn(1, 100)) } },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            // Interval Range Section
            Column(modifier = Modifier.weight(1.5f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.interval_range), style = labelStyle)
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntervalField(
                        label = "Min Int", configValue = cfg.minIntervalMin, enabled = !cfg.isActive, textStyle = bodyStyle, labelStyle = labelStyle,
                        onCommit = { v -> vm.updateConfig { copy(minIntervalMin = v.coerceIn(1, cfg.maxIntervalMin)) } },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    IntervalField(
                        label = "Max Int", configValue = cfg.maxIntervalMin, enabled = !cfg.isActive, textStyle = bodyStyle, labelStyle = labelStyle,
                        onCommit = { v -> vm.updateConfig { copy(maxIntervalMin = v.coerceAtLeast(cfg.minIntervalMin)) } },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            // Alerts Section
            Column(modifier = Modifier.weight(1.2f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.section_alerts), style = labelStyle)
                Card(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                        ToggleRow(stringResource(R.string.alert_sound), cfg.soundEnabled, bodyStyle, scaleFactor) { vm.updateConfig { copy(soundEnabled = it) } }
                        ToggleRow(stringResource(R.string.alert_vibration), cfg.vibrationEnabled, bodyStyle, scaleFactor) { vm.updateConfig { copy(vibrationEnabled = it) } }
                    }
                }
            }

            // Repeat Days Section
            Column(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.section_repeat), style = labelStyle)
                Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (dow in DayOfWeek.entries) {
                        val selected = cfg.repeatDays.contains(dow.value)
                        FilterChip(
                            selected = selected, enabled = !cfg.isActive,
                            onClick = {
                                val newDays = if (selected) cfg.repeatDays - dow.value else cfg.repeatDays + dow.value
                                vm.updateConfig { copy(repeatDays = newDays) }
                            },
                            label = { Text(dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()), fontSize = labelStyle.fontSize) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = MaterialTheme.shapes.extraSmall
                        )
                    }
                }
            }

            if (cfg.windowMinutes < cfg.minIntervalMin) {
                Text(stringResource(R.string.warning_window_too_small), color = MaterialTheme.colorScheme.error, style = bodyStyle)
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────

@Composable
private fun ToggleRow(label: String, checked: Boolean, textStyle: androidx.compose.ui.text.TextStyle, scale: Float, onToggle: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = textStyle)
        Switch(checked = checked, onCheckedChange = onToggle, modifier = Modifier.scale(0.7f * scale))
    }
}

@Composable
private fun DurationPickerCard(
    seconds: Long, enabled: Boolean, textStyle: androidx.compose.ui.text.TextStyle,
    onPick: (Long) -> Unit, modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60

    OutlinedCard(onClick = { if (enabled) showDialog = true }, modifier = modifier, enabled = enabled) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("%02d:%02d:%02d".format(h, m, s), style = textStyle)
        }
    }
    // ... Dialog logic remains the same ...
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
    label: String, hour: Int, minute: Int, enabled: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle, labelStyle: androidx.compose.ui.text.TextStyle,
    onPick: (Int, Int) -> Unit, modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val tpState = rememberTimePickerState(initialHour = hour, initialMinute = minute)

    OutlinedCard(onClick = { if (enabled) showDialog = true }, modifier = modifier, enabled = enabled) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(label, style = labelStyle)
            Text(fmtTime(hour, minute), style = textStyle)
        }
    }
    // ... Dialog logic remains the same ...
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
    label: String, configValue: Int, enabled: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle, labelStyle: androidx.compose.ui.text.TextStyle,
    onCommit: (Int) -> Unit, modifier: Modifier = Modifier
) {
    var text by remember(configValue) { mutableStateOf(configValue.toString()) }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            if (newText.isEmpty() || newText.all { it.isDigit() }) {
                text = newText
                val parsed = newText.toIntOrNull()
                if (parsed != null && parsed > 0) onCommit(parsed)
            }
        },
        label = { Text(label, style = labelStyle) },
        enabled = enabled, singleLine = true, textStyle = textStyle,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

private fun fmtTime(h: Int, m: Int): String =
    "%02d:%02d".format(h, m)
