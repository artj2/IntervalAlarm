package com.example.intervalalarm.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.intervalalarm.R
import com.example.intervalalarm.service.TimerService

@Composable
fun TimerScreen(onReturn: () -> Unit) {
    val remaining by TimerService.remainingSeconds.collectAsState()
    val isFinished by TimerService.isFinished.collectAsState()
    val decisionRemaining by TimerService.decisionTimerSeconds.collectAsState()
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
                    val intent = Intent(ctx, TimerService::class.java).apply {
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
            // Congratulations Screen
            Text(
                stringResource(R.string.msg_congrats),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "🐶",
                fontSize = 80.sp,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Decision Timer with Circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(100.dp)
            ) {
                CircularProgressIndicator(
                    progress = { decisionRemaining / 10f },
                    modifier = Modifier.fillMaxSize(),
                    strokeWidth = 8.dp,
                    color = if (decisionRemaining > 0) MaterialTheme.colorScheme.primary else Color.Transparent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    text = decisionRemaining.toString(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (decisionRemaining == 0) {
                Text(
                    stringResource(R.string.msg_defaulting_success),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        val intent = Intent(ctx, TimerService::class.java).apply {
                            action = TimerService.ACTION_SUCCESS
                        }
                        ctx.startService(intent)
                        onReturn()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.btn_success))
                }

                Button(
                    onClick = {
                        val intent = Intent(ctx, TimerService::class.java).apply {
                            action = TimerService.ACTION_FAILED
                        }
                        ctx.startService(intent)
                        onReturn()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_failed))
                }
            }
        }
    }

    // Auto-return when service stops
    LaunchedEffect(remaining, isFinished) {
        if (remaining == 0L && !isFinished) {
            onReturn()
        }
    }
}

private fun fmtSeconds(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}
