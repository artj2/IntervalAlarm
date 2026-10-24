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
import com.example.intervalalarm.service.AlarmFiringService
import com.example.intervalalarm.service.TimerService
import com.example.intervalalarm.ui.AlarmViewModel
import com.example.intervalalarm.ui.screens.HistoryScreen
import com.example.intervalalarm.ui.screens.HomeScreen
import com.example.intervalalarm.ui.screens.TimerScreen
import com.example.intervalalarm.ui.theme.IntervalAlarmTheme

class MainActivity : ComponentActivity() {

    private var pendingIntent by mutableStateOf<Intent?>(null)

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            pendingIntent = intent
        }

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
                    AppContent(
                        pendingIntent = pendingIntent,
                        onIntentConsumed = { pendingIntent = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent = intent
    }
}

enum class Screen { HOME, HISTORY, TIMER }

@Composable
private fun AppContent(
    pendingIntent: Intent?,
    onIntentConsumed: () -> Unit,
    vm: AlarmViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(Screen.HOME) }
    val ctx = LocalContext.current

    // Check if we should navigate to timer from intent or if service is already running
    val remaining by TimerService.remainingSeconds.collectAsState()
    val isFinished by TimerService.isFinished.collectAsState()
    val isFiring by AlarmFiringService.isFiring.collectAsState()

    LaunchedEffect(pendingIntent) {
        val currentIntent = pendingIntent ?: return@LaunchedEffect
        val action = currentIntent.action
        val navToTimer = currentIntent.getBooleanExtra("navigate_to_timer", false)
        currentIntent.action = null
        currentIntent.removeExtra("navigate_to_timer")
        onIntentConsumed()

        if (action == AlarmFiringService.ACTION_ACCEPT_FROM_NOTIF) {
            if (AlarmFiringService.isFiring.value) {
                val startIntent = Intent(ctx, AlarmFiringService::class.java).apply {
                    this.action = AlarmFiringService.ACTION_ACCEPT
                }
                ctx.startService(startIntent)
                selectedTab = Screen.TIMER
            }
        } else if (navToTimer && (remaining > 0 || isFinished)) {
            selectedTab = Screen.TIMER
        }
    }

    LaunchedEffect(remaining, isFinished, isFiring) {
        if (remaining > 0 || isFinished) {
            selectedTab = Screen.TIMER
        } else if (!isFiring && selectedTab == Screen.TIMER) {
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
