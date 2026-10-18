package com.example.intervalalarm.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.intervalalarm.alarm.AlarmScheduler
import com.example.intervalalarm.data.AlarmHistoryDatabase
import com.example.intervalalarm.data.AlarmHistoryEntry
import com.example.intervalalarm.data.AlarmPreferences
import com.example.intervalalarm.model.AlarmConfig
import com.example.intervalalarm.service.AlarmFiringService
import com.example.intervalalarm.service.TimerService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AlarmViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx get() = getApplication<Application>()
    private val historyDao = AlarmHistoryDatabase.get(ctx).dao()

    val config: StateFlow<AlarmConfig> =
        AlarmPreferences.configFlow(ctx)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlarmConfig())

    val history: StateFlow<List<AlarmHistoryEntry>> =
        historyDao.getAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateConfig(transform: AlarmConfig.() -> AlarmConfig) {
        viewModelScope.launch {
            val updated = config.value.transform()
            AlarmPreferences.save(ctx, updated)
        }
    }

    fun toggleActive() {
        viewModelScope.launch {
            val cfg = config.value
            if (cfg.isActive) {
                AlarmScheduler.cancel(ctx)
                AlarmPreferences.setActive(ctx, false)
            } else {
                if (cfg.windowMinutes < cfg.minIntervalMin) return@launch
                AlarmPreferences.setActive(ctx, true)
                val activeCfg = cfg.copy(isActive = true)
                AlarmScheduler.schedule(ctx, activeCfg)
            }
        }
    }

    fun startManualAloneTime() {
        viewModelScope.launch {
            if (TimerService.remainingSeconds.value > 0 || TimerService.isFinished.value) {
                return@launch
            }

            if (AlarmFiringService.isFiring.value) {
                val intent = Intent(ctx, AlarmFiringService::class.java).apply {
                    action = AlarmFiringService.ACTION_ACCEPT
                }
                ctx.startForegroundService(intent)
                return@launch
            }

            val cfg = config.value
            if (cfg.isActive) {
                AlarmScheduler.cancel(ctx)
            }
            val historyId = historyDao.insert(
                AlarmHistoryEntry(
                    status = "FIRED",
                    initialTimeAloneSeconds = cfg.timeAloneSeconds
                )
            )

            val intent = Intent(ctx, TimerService::class.java).apply {
                action = TimerService.ACTION_START
                putExtra(TimerService.EXTRA_SECONDS, cfg.timeAloneSeconds)
                putExtra(TimerService.EXTRA_ALMOST_DONE_SECONDS, cfg.almostDoneSeconds)
                putExtra(TimerService.EXTRA_HISTORY_ID, historyId)
            }
            ctx.startForegroundService(intent)
        }
    }

    fun checkAndRescheduleIfOverdue() {
        viewModelScope.launch {
            val cfg = config.value
            val isBusy = AlarmFiringService.isFiring.value || TimerService.remainingSeconds.value > 0 || TimerService.isFinished.value
            if (cfg.isActive && !isBusy) {
                val overdue = cfg.nextTriggerMillis != null && cfg.nextTriggerMillis < System.currentTimeMillis() - 60_000L
                val missing = cfg.nextTriggerMillis == null
                if (overdue || missing) {
                    AlarmScheduler.schedule(ctx, cfg)
                }
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { historyDao.clearAll() }
    }
}
