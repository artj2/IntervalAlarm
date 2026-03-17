package com.example.intervalalarm

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.intervalalarm.service.TimerService
import com.example.intervalalarm.ui.AlarmViewModel
import com.example.intervalalarm.ui.screens.HistoryScreen
import com.example.intervalalarm.ui.screens.HomeScreen
import com.example.intervalalarm.ui.screens.TimerScreen
import com.example.intervalalarm.ui.theme.IntervalAlarmTheme

class MainActivity : ComponentActivity() {

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            IntervalAlarmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(intent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

enum class Screen { HOME, HISTORY, TIMER }

@Composable
private fun AppContent(intent: Intent, vm: AlarmViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(Screen.HOME) }
    val ctx = LocalContext.current
    
    // Check if we should navigate to timer from intent or if service is already running
    val remaining by TimerService.remainingSeconds.collectAsState()
    val isFinished by TimerService.isFinished.collectAsState()
    
    LaunchedEffect(intent, remaining, isFinished) {
        val action = intent.action
        if (action == "com.example.intervalalarm.ACTION_ACCEPT_FROM_NOTIF") {
            val startIntent = Intent(ctx, com.example.intervalalarm.service.AlarmFiringService::class.java).apply {
                this.action = com.example.intervalalarm.service.AlarmFiringService.ACTION_ACCEPT
            }
            ctx.startForegroundService(startIntent)
            selectedTab = Screen.TIMER
            intent.action = null // Clear to prevent re-trigger
        } else if (intent.getBooleanExtra("navigate_to_timer", false) || remaining > 0 || isFinished) {
            selectedTab = Screen.TIMER
        }
    }

    LaunchedEffect(remaining, isFinished) {
        if (remaining == 0L && !isFinished && selectedTab == Screen.TIMER) {
            selectedTab = Screen.HOME
        }
    }

    Scaffold(
        bottomBar = {
            if (selectedTab != Screen.TIMER) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == Screen.HOME,
                        onClick = { selectedTab = Screen.HOME },
                        icon = { Icon(Icons.Default.Home, contentDescription = stringResource(R.string.nav_alarm)) },
                        label = { Text(stringResource(R.string.nav_alarm)) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == Screen.HISTORY,
                        onClick = { selectedTab = Screen.HISTORY },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.nav_history)) },
                        label = { Text(stringResource(R.string.nav_history)) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                Screen.HOME -> HomeScreen(vm)
                Screen.HISTORY -> HistoryScreen(vm)
                Screen.TIMER -> TimerScreen(onReturn = { selectedTab = Screen.HOME })
            }
        }
    }
}
