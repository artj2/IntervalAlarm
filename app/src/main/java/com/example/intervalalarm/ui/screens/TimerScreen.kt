package com.example.intervalalarm.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.intervalalarm.R
import com.example.intervalalarm.service.TimerService

@Composable
fun TimerScreen(onReturn: () -> Unit) {
    val remaining by TimerService.remainingSeconds.collectAsState()
    val isFinished by TimerService.isFinished.collectAsState()
    val ctx = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!isFinished) {
            Text(
                stringResource(R.string.timer_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                fmtSeconds(remaining),
                style = MaterialTheme.typography.displayLarge
            )
            Spacer(modifier = Modifier.height(64.dp))
            Button(
                onClick = {
                    val intent = android.content.Intent(ctx, TimerService::class.java).apply {
                        action = TimerService.ACTION_FAILED
                    }
                    ctx.startService(intent)
                    onReturn()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.btn_failed))
            }
        } else {
            Text(
                stringResource(R.string.msg_congrats),
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(modifier = Modifier.height(64.dp))
            Button(onClick = {
                TimerService.stop(ctx)
                onReturn()
            }) {
                Text(stringResource(R.string.btn_return))
            }
        }
    }
}

private fun fmtSeconds(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
