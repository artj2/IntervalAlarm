package com.example.intervalalarm.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                    "${entries.size} alarm${if (entries.size != 1) "s" else ""} logged",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { showClearDialog = true }) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No alarms fired yet",
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
            title = { Text("Clear History") },
            text = { Text("Delete all alarm history entries? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearHistory()
                    showClearDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
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
    val text = remember(entry.timestamp) {
        Instant.ofEpochMilli(entry.timestamp)
            .atZone(ZoneId.systemDefault())
            .format(fmt)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
