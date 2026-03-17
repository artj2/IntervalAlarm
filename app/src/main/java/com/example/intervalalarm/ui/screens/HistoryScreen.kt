package com.example.intervalalarm.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.example.intervalalarm.R
import com.example.intervalalarm.data.AlarmHistoryEntry
import com.example.intervalalarm.ui.AlarmViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(vm: AlarmViewModel) {
    val entries by vm.history.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (entries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    pluralStringResource(R.plurals.alarms_logged, entries.size, entries.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { showClearDialog = true }) {
                    Text(stringResource(R.string.btn_clear_all), color = MaterialTheme.colorScheme.error)
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
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_title)) },
            text = { Text(stringResource(R.string.dialog_clear_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearHistory()
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.btn_clear), color = MaterialTheme.colorScheme.error)
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
