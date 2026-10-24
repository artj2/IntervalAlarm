package com.example.intervalalarm.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.intervalalarm.R
import com.example.intervalalarm.alarm.AlarmScheduler
import com.example.intervalalarm.ui.AlarmViewModel
import com.example.intervalalarm.service.AlarmFiringService
import kotlinx.coroutines.delay
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
    val focusManager = LocalFocusManager.current

    var minText by remember(cfg.minIntervalMin) { mutableStateOf(cfg.minIntervalMin.toString()) }
    var maxText by remember(cfg.maxIntervalMin) { mutableStateOf(cfg.maxIntervalMin.toString()) }
    var adjText by remember(cfg.adjustmentPercent) { mutableStateOf(cfg.adjustmentPercent.toString()) }

    val resolveValidatedFields: () -> Triple<Int, Int, Int> = {
        val currentMin = (minText.toIntOrNull() ?: cfg.minIntervalMin).coerceAtLeast(1)
        val currentMax = (maxText.toIntOrNull() ?: cfg.maxIntervalMin).coerceAtLeast(1)
        val validMax = maxOf(currentMin, currentMax)
        val validMin = currentMin.coerceIn(1, validMax)
        val validAdj = (adjText.toIntOrNull() ?: cfg.adjustmentPercent).coerceIn(1, 100)

        minText = validMin.toString()
        maxText = validMax.toString()
        adjText = validAdj.toString()

        Triple(validMin, validMax, validAdj)
    }

    val commitFields: () -> Unit = {
        val (validMin, validMax, validAdj) = resolveValidatedFields()
        if (validMin != cfg.minIntervalMin || validMax != cfg.maxIntervalMin || validAdj != cfg.adjustmentPercent) {
            vm.updateConfig {
                copy(
                    minIntervalMin = validMin,
                    maxIntervalMin = validMax,
                    adjustmentPercent = validAdj
                )
            }
        }
    }

    val onMinTextChange: (String) -> Unit = { newText ->
        val currentMax = maxText.toIntOrNull()?.coerceAtLeast(1) ?: cfg.maxIntervalMin
        val parsed = newText.toIntOrNull()
        if (newText.isEmpty() || (newText.all { it.isDigit() } && newText.length <= 4 && parsed != null && parsed in 1..currentMax)) {
            minText = newText
        }
    }

    val onMaxTextChange: (String) -> Unit = { newText ->
        val parsed = newText.toIntOrNull()
        if (newText.isEmpty() || (newText.all { it.isDigit() } && newText.length <= 4 && parsed != null && parsed >= 1)) {
            maxText = newText
        }
    }

    val onAdjTextChange: (String) -> Unit = { newText ->
        val parsed = newText.toIntOrNull()
        if (newText.isEmpty() || (newText.all { it.isDigit() } && newText.length <= 3 && parsed != null && parsed in 1..100)) {
            adjText = newText
        }
    }

    val currentTime by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(10_000L)
            value = System.currentTimeMillis()
        }
    }
    var showSettings by remember(cfg.isActive) { mutableStateOf(false) }

    LaunchedEffect(cfg.isActive) {
        vm.checkAndRescheduleIfOverdue()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val scaleFactor = (screenHeight / 700.dp).coerceIn(0.8f, 1.5f)
        val labelStyle = MaterialTheme.typography.labelMedium.copy(fontSize = (MaterialTheme.typography.labelMedium.fontSize * scaleFactor))
        val bodyStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = (MaterialTheme.typography.bodyMedium.fontSize * scaleFactor))
        val titleStyle = MaterialTheme.typography.titleMedium.copy(fontSize = (MaterialTheme.typography.titleMedium.fontSize * scaleFactor))
        val largeStyle = MaterialTheme.typography.headlineMedium.copy(fontSize = (MaterialTheme.typography.headlineMedium.fontSize * scaleFactor))
        val sectionHeaderStyle = MaterialTheme.typography.titleSmall.copy(
            fontSize = (MaterialTheme.typography.titleSmall.fontSize * scaleFactor),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        val sectionTopPadding = (14.dp * scaleFactor).coerceIn(10.dp, 20.dp)
        val sectionBottomPadding = (4.dp * scaleFactor).coerceIn(2.dp, 6.dp)

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
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (cfg.isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = (10.dp * scaleFactor).coerceIn(8.dp, 16.dp)),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            if (cfg.isActive) stringResource(R.string.status_active) else stringResource(R.string.status_inactive),
                            style = titleStyle,
                            color = if (cfg.isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (cfg.isActive) {
                            Text(
                                stringResource(R.string.running_range, fmtTime(cfg.startHour, cfg.startMinute), fmtTime(cfg.endHour, cfg.endMinute)),
                                style = bodyStyle,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                commitFields()
                                focusManager.clearFocus()
                                vm.startManualAloneTime()
                            },
                            modifier = Modifier.height(44.dp * scaleFactor),
                            contentPadding = PaddingValues(horizontal = (12.dp * scaleFactor).coerceIn(8.dp, 16.dp))
                        ) {
                            Text(stringResource(R.string.btn_alone_time), fontSize = bodyStyle.fontSize)
                        }
                        FilledTonalButton(
                            onClick = {
                                val (validMin, validMax, validAdj) = resolveValidatedFields()
                                focusManager.clearFocus()
                                vm.saveAndToggleActive(validMin, validMax, validAdj)
                            },
                            colors = if (cfg.isActive) {
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFFFFD1DC),
                                    contentColor = Color(0xFF4A151C)
                                )
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            },
                            modifier = Modifier.height(44.dp * scaleFactor),
                            contentPadding = PaddingValues(horizontal = (12.dp * scaleFactor).coerceIn(8.dp, 16.dp))
                        ) {
                            Text(if (cfg.isActive) stringResource(R.string.btn_stop) else stringResource(R.string.btn_start), fontSize = bodyStyle.fontSize)
                        }
                    }
                }
            }

            if (cfg.isActive) {
                // Next Alone Time Section (under "Alarm Active" and above "Alerts")
                NextAloneTimeSection(
                    cfg = cfg,
                    nowMillis = currentTime,
                    headerStyle = sectionHeaderStyle,
                    scaleFactor = scaleFactor,
                    topPadding = sectionTopPadding,
                    bottomPadding = sectionBottomPadding
                )

                // When active: Alerts section is always visible and editable
                AlertsSection(
                    cfg = cfg, vm = vm,
                    headerStyle = sectionHeaderStyle, bodyStyle = bodyStyle, scaleFactor = scaleFactor,
                    topPadding = sectionTopPadding, bottomPadding = sectionBottomPadding
                )

                // View Settings / Hide Settings toggle button
                OutlinedButton(
                    onClick = { showSettings = !showSettings },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = sectionTopPadding, bottom = sectionBottomPadding)
                ) {
                    Icon(
                        imageVector = if (showSettings) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(if (showSettings) R.string.btn_hide_settings else R.string.btn_view_settings),
                        fontSize = bodyStyle.fontSize
                    )
                }

                if (showSettings) {
                    TimeWindowSection(cfg, vm, false, sectionHeaderStyle, largeStyle, labelStyle, sectionTopPadding, sectionBottomPadding)
                    TimeAloneSection(cfg, vm, adjText, onAdjTextChange, commitFields, false, sectionHeaderStyle, largeStyle, bodyStyle, labelStyle, sectionTopPadding, sectionBottomPadding)
                    IntervalRangeSection(minText, onMinTextChange, maxText, onMaxTextChange, commitFields, false, sectionHeaderStyle, bodyStyle, labelStyle, sectionTopPadding, sectionBottomPadding)
                    AlmostDoneSection(cfg, vm, false, sectionHeaderStyle, largeStyle, sectionTopPadding, sectionBottomPadding)
                    RepeatDaysSection(cfg, vm, false, sectionHeaderStyle, labelStyle, sectionTopPadding, sectionBottomPadding)
                }
            } else {
                TimeWindowSection(cfg, vm, true, sectionHeaderStyle, largeStyle, labelStyle, sectionTopPadding, sectionBottomPadding)
                TimeAloneSection(cfg, vm, adjText, onAdjTextChange, commitFields, true, sectionHeaderStyle, largeStyle, bodyStyle, labelStyle, sectionTopPadding, sectionBottomPadding)
                IntervalRangeSection(minText, onMinTextChange, maxText, onMaxTextChange, commitFields, true, sectionHeaderStyle, bodyStyle, labelStyle, sectionTopPadding, sectionBottomPadding)
                AlertsSection(cfg, vm, sectionHeaderStyle, bodyStyle, scaleFactor, sectionTopPadding, sectionBottomPadding)
                AlmostDoneSection(cfg, vm, true, sectionHeaderStyle, largeStyle, sectionTopPadding, sectionBottomPadding)
                RepeatDaysSection(cfg, vm, true, sectionHeaderStyle, labelStyle, sectionTopPadding, sectionBottomPadding)

                val effectiveMin = minText.toIntOrNull() ?: cfg.minIntervalMin
                if (cfg.windowMinutes < effectiveMin) {
                    Text(
                        stringResource(R.string.warning_window_too_small),
                        color = MaterialTheme.colorScheme.error,
                        style = bodyStyle,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ── Sections ─────────────────────────────────────────────

@Composable
private fun NextAloneTimeSection(
    cfg: com.example.intervalalarm.model.AlarmConfig,
    nowMillis: Long,
    headerStyle: androidx.compose.ui.text.TextStyle,
    scaleFactor: Float,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    val ctx = LocalContext.current
    val display = remember(cfg.nextTriggerMillis, nowMillis) {
        AlarmScheduler.getNextTriggerDisplay(ctx, cfg.nextTriggerMillis, nowMillis)
    }

    Text(
        stringResource(R.string.section_next_alone_time),
        style = headerStyle,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding)
    )
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = (18.dp * scaleFactor).coerceIn(14.dp, 26.dp), horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = display.timeText,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = (MaterialTheme.typography.displaySmall.fontSize * scaleFactor),
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (display.countdownText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp * scaleFactor))
                Text(
                    text = display.countdownText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = (MaterialTheme.typography.titleMedium.fontSize * scaleFactor),
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun TimeWindowSection(
    cfg: com.example.intervalalarm.model.AlarmConfig,
    vm: AlarmViewModel,
    enabled: Boolean,
    headerStyle: androidx.compose.ui.text.TextStyle,
    largeStyle: androidx.compose.ui.text.TextStyle,
    labelStyle: androidx.compose.ui.text.TextStyle,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    Text(
        stringResource(R.string.time_window),
        style = headerStyle,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding)
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TimePickerCard(
            label = stringResource(R.string.label_start),
            hour = cfg.startHour, minute = cfg.startMinute,
            enabled = enabled, textStyle = largeStyle, labelStyle = labelStyle,
            onPick = { h, m -> vm.updateConfig { copy(startHour = h, startMinute = m) } },
            modifier = Modifier.weight(1f)
        )
        TimePickerCard(
            label = stringResource(R.string.label_end),
            hour = cfg.endHour, minute = cfg.endMinute,
            enabled = enabled, textStyle = largeStyle, labelStyle = labelStyle,
            onPick = { h, m -> vm.updateConfig { copy(endHour = h, endMinute = m) } },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TimeAloneSection(
    cfg: com.example.intervalalarm.model.AlarmConfig,
    vm: AlarmViewModel,
    adjText: String,
    onAdjTextChange: (String) -> Unit,
    onCommit: () -> Unit,
    enabled: Boolean,
    headerStyle: androidx.compose.ui.text.TextStyle,
    largeStyle: androidx.compose.ui.text.TextStyle,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    labelStyle: androidx.compose.ui.text.TextStyle,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.label_time_alone),
            style = headerStyle,
            modifier = Modifier.weight(1.5f)
        )
        Text(
            stringResource(R.string.label_adjustment),
            style = headerStyle,
            modifier = Modifier.weight(1f)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DurationPickerCard(
            seconds = cfg.timeAloneSeconds, enabled = enabled, textStyle = largeStyle,
            onPick = { vm.updateConfig { copy(timeAloneSeconds = it) } },
            modifier = Modifier.weight(1.5f)
        )
        IntervalField(
            label = "%", value = adjText, enabled = enabled, textStyle = bodyStyle, labelStyle = labelStyle,
            onValueChange = onAdjTextChange,
            onCommit = onCommit,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun IntervalRangeSection(
    minText: String,
    onMinTextChange: (String) -> Unit,
    maxText: String,
    onMaxTextChange: (String) -> Unit,
    onCommit: () -> Unit,
    enabled: Boolean,
    headerStyle: androidx.compose.ui.text.TextStyle,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    labelStyle: androidx.compose.ui.text.TextStyle,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    Text(
        stringResource(R.string.interval_range),
        style = headerStyle,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding)
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IntervalField(
            label = "Min Int", value = minText, enabled = enabled, textStyle = bodyStyle, labelStyle = labelStyle,
            onValueChange = onMinTextChange,
            onCommit = onCommit,
            modifier = Modifier.weight(1f)
        )
        IntervalField(
            label = "Max Int", value = maxText, enabled = enabled, textStyle = bodyStyle, labelStyle = labelStyle,
            onValueChange = onMaxTextChange,
            onCommit = onCommit,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AlertsSection(
    cfg: com.example.intervalalarm.model.AlarmConfig,
    vm: AlarmViewModel,
    headerStyle: androidx.compose.ui.text.TextStyle,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    scaleFactor: Float,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    Text(
        stringResource(R.string.section_alerts),
        style = headerStyle,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding)
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = (4.dp * scaleFactor).coerceIn(2.dp, 8.dp)),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ToggleRow(stringResource(R.string.alert_sound), cfg.soundEnabled, bodyStyle, scaleFactor) { vm.updateConfig { copy(soundEnabled = it) } }
            ToggleRow(stringResource(R.string.alert_vibration), cfg.vibrationEnabled, bodyStyle, scaleFactor) { vm.updateConfig { copy(vibrationEnabled = it) } }
        }
    }
}

@Composable
private fun AlmostDoneSection(
    cfg: com.example.intervalalarm.model.AlarmConfig,
    vm: AlarmViewModel,
    enabled: Boolean,
    headerStyle: androidx.compose.ui.text.TextStyle,
    largeStyle: androidx.compose.ui.text.TextStyle,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    Text(
        stringResource(R.string.section_almost_done),
        style = headerStyle,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding)
    )
    AlmostDonePickerCard(
        seconds = cfg.almostDoneSeconds,
        enabled = enabled,
        textStyle = largeStyle,
        onPick = { vm.updateConfig { copy(almostDoneSeconds = it) } }
    )
}

@Composable
private fun RepeatDaysSection(
    cfg: com.example.intervalalarm.model.AlarmConfig,
    vm: AlarmViewModel,
    enabled: Boolean,
    headerStyle: androidx.compose.ui.text.TextStyle,
    labelStyle: androidx.compose.ui.text.TextStyle,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp
) {
    Text(
        stringResource(R.string.section_repeat),
        style = headerStyle,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding)
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (dow in DayOfWeek.entries) {
            val selected = cfg.repeatDays.contains(dow.value)
            FilterChip(
                selected = selected, enabled = enabled,
                onClick = {
                    val newDays = if (selected) cfg.repeatDays - dow.value else cfg.repeatDays + dow.value
                    vm.updateConfig { copy(repeatDays = newDays) }
                },
                label = { Text(dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()), fontSize = labelStyle.fontSize) },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraSmall
            )
        }
    }
    if (cfg.repeatDays.isEmpty()) {
        Text(
            stringResource(R.string.repeat_hint),
            style = labelStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            modifier = Modifier.padding(top = 4.dp)
        )
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
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

@Composable
private fun AlmostDonePickerCard(
    seconds: Long,
    enabled: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle,
    onPick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val m = seconds / 60
    val s = seconds % 60

    OutlinedCard(onClick = { if (enabled) showDialog = true }, modifier = modifier, enabled = enabled) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            val display = if (seconds == 0L) {
                stringResource(R.string.label_off)
            } else {
                "%02d:%02d".format(m, s)
            }
            Text(display, style = textStyle)
        }
    }

    if (showDialog) {
        var localM by remember { mutableStateOf(m.toString()) }
        var localS by remember { mutableStateOf(s.toString()) }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val newM = localM.toLongOrNull() ?: 0L
                    val newS = localS.toLongOrNull() ?: 0L
                    val total = (newM * 60 + newS).coerceIn(0L, 3600L)
                    onPick(total)
                    showDialog = false
                }) { Text(stringResource(R.string.dialog_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            },
            title = { Text(stringResource(R.string.section_almost_done)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.hint_almost_done_dialog),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = localM,
                            onValueChange = { if (it.length <= 2 && it.all { char -> char.isDigit() }) localM = it },
                            label = { Text("M") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = localS,
                            onValueChange = { if (it.length <= 2 && it.all { char -> char.isDigit() }) localS = it },
                            label = { Text("S") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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
    label: String,
    value: String,
    enabled: Boolean,
    textStyle: androidx.compose.ui.text.TextStyle,
    labelStyle: androidx.compose.ui.text.TextStyle,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var wasFocused by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = labelStyle) },
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                onCommit()
                focusManager.clearFocus()
            }
        ),
        modifier = modifier.onFocusChanged { focusState ->
            if (focusState.isFocused) {
                wasFocused = true
            } else if (wasFocused) {
                wasFocused = false
                onCommit()
            }
        }
    )
}

private fun fmtTime(h: Int, m: Int): String =
    "%02d:%02d".format(h, m)
