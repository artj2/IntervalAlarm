package com.example.intervalalarm.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.intervalalarm.BuildConfig
import com.example.intervalalarm.R
import com.example.intervalalarm.data.AlarmHistoryEntry
import com.example.intervalalarm.ui.AlarmViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class HistoryClearOption(val labelRes: Int) {
    KEEP_TODAY(R.string.clear_option_keep_today),
    KEEP_7_DAYS(R.string.clear_option_keep_7_days),
    KEEP_30_DAYS(R.string.clear_option_keep_30_days),
    CLEAR_ALL(R.string.clear_option_clear_all);

    fun cutoffMillis(zone: ZoneId): Long? {
        val today = LocalDate.now(zone)
        return when (this) {
            KEEP_TODAY -> today.atStartOfDay(zone).toInstant().toEpochMilli()
            KEEP_7_DAYS -> today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
            KEEP_30_DAYS -> today.minusDays(29).atStartOfDay(zone).toInstant().toEpochMilli()
            CLEAR_ALL -> null
        }
    }
}

@Composable
fun HistoryScreen(vm: AlarmViewModel) {
    val entries by vm.history.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                pluralStringResource(R.plurals.alarms_logged, entries.size, entries.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (entries.isNotEmpty()) {
                    TextButton(onClick = { showClearDialog = true }) {
                        Text(stringResource(R.string.btn_clear), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    HistoryItem(entry)
                }
            }
        }
    }

    if (showClearDialog) {
        var selectedOption by remember { mutableStateOf(HistoryClearOption.KEEP_30_DAYS) }
        val zone = ZoneId.systemDefault()
        val cutoffMillis = selectedOption.cutoffMillis(zone)
        val deleteCount = if (cutoffMillis == null) {
            entries.size
        } else {
            entries.count { it.timestamp < cutoffMillis }
        }

        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.selectableGroup()) {
                        for (option in HistoryClearOption.entries) {
                            val selected = option == selectedOption
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = selected,
                                        onClick = { selectedOption = option },
                                        role = Role.RadioButton
                                    )
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(option.labelRes),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                    Text(
                        text = if (deleteCount > 0) {
                            pluralStringResource(
                                R.plurals.dialog_clear_preview,
                                entries.size,
                                deleteCount,
                                entries.size
                            )
                        } else {
                            stringResource(R.string.dialog_clear_none_to_delete)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (deleteCount > 0) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteCount > 0,
                    onClick = {
                        if (cutoffMillis == null) {
                            vm.clearHistory()
                        } else {
                            vm.clearHistoryBefore(cutoffMillis)
                        }
                        showClearDialog = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.btn_clear),
                        color = if (deleteCount > 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun HistoryItem(entry: AlarmHistoryEntry) {
    val fmt = remember {
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy — HH:mm:ss")
    }
    val dateText = remember(entry.timestamp) {
        Instant.ofEpochMilli(entry.timestamp)
            .atZone(ZoneId.systemDefault())
            .format(fmt)
    }

    val statusDetails = when (entry.status) {
        "DISMISSED" -> "Dismissed"
        "INTERRUPTED" -> stringResource(R.string.history_status_interrupted)
        "SUCCESS" -> "Success — ${fmtSeconds(entry.initialTimeAloneSeconds)}"
        "FAILED" -> "Failed — ${fmtSeconds(entry.elapsedTimeSeconds)} of ${fmtSeconds(entry.initialTimeAloneSeconds)}"
        else -> "Fired"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = statusDetails,
                style = MaterialTheme.typography.bodyMedium,
                color = if (entry.status == "SUCCESS") MaterialTheme.colorScheme.primary 
                        else if (entry.status == "FAILED") MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun fmtSeconds(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
