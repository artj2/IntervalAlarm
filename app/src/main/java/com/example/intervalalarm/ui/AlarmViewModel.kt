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
            AlarmPreferences.update(ctx, transform)
        }
    }

    fun saveAndToggleActive(minInterval: Int, maxInterval: Int, adjustmentPercent: Int) {
        viewModelScope.launch {
            val cfg = AlarmPreferences.update(ctx) {
                copy(
                    minIntervalMin = minInterval,
                    maxIntervalMin = maxInterval,
                    adjustmentPercent = adjustmentPercent
                )
            }
            toggleActiveWithConfig(cfg)
        }
    }

    fun toggleActive() {
        viewModelScope.launch {
            val cfg = config.value
            toggleActiveWithConfig(cfg)
        }
    }

    private suspend fun toggleActiveWithConfig(cfg: AlarmConfig) {
        if (cfg.isActive) {
            AlarmScheduler.cancel(ctx)
            AlarmPreferences.setActive(ctx, false)
        } else {
            if (cfg.windowMinutes < cfg.minIntervalMin) return
            val zone = java.time.ZoneId.systemDefault()
            val now = java.time.ZonedDateTime.now(zone)
            val todayStart = now.toLocalDate().atTime(cfg.startHour, cfg.startMinute).atZone(zone)
            val todayEnd = now.toLocalDate().atTime(cfg.endHour, cfg.endMinute).atZone(zone)
            val minIntervalMs = cfg.minIntervalMin * 60_000L
            val remainingMsToday = if (now.isBefore(todayStart)) {
                java.time.Duration.between(todayStart, todayEnd).toMillis()
            } else if (now.isBefore(todayEnd)) {
                java.time.Duration.between(now, todayEnd).toMillis()
            } else {
                0L
            }

            if (remainingMsToday < minIntervalMs) {
                android.widget.Toast.makeText(
                    ctx,
                    ctx.getString(com.example.intervalalarm.R.string.msg_not_enough_time_today),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                if (cfg.repeatDays.isEmpty()) {
                    return
                }
            }
            AlarmPreferences.setActive(ctx, true)
            val activeCfg = cfg.copy(isActive = true)
            AlarmScheduler.schedule(ctx, activeCfg)
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
