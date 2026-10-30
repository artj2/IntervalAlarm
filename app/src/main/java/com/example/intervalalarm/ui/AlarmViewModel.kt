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

    private fun stopFiringAlarmIfNeeded() {
        if (AlarmFiringService.isFiring.value) {
            val intent = Intent(ctx, AlarmFiringService::class.java).apply {
                action = AlarmFiringService.ACTION_DISMISS
            }
            ctx.startService(intent)
        }
    }

    fun saveAndToggleActive(
        minInterval: Int,
        maxInterval: Int,
        adjustmentPercent: Int,
        includeTodayOverride: Boolean = false
    ) {
        viewModelScope.launch {
            if (config.value.isActive) {
                AlarmScheduler.cancel(ctx, clearPreference = false)
                AlarmPreferences.update(ctx) {
                    copy(
                        minIntervalMin = minInterval,
                        maxIntervalMin = maxInterval,
                        adjustmentPercent = adjustmentPercent,
                        isActive = false,
                        nextTriggerMillis = null,
                        overrideTodayDate = null
                    )
                }
                stopFiringAlarmIfNeeded()
            } else {
                val todayStr = java.time.LocalDate.now(java.time.ZoneId.systemDefault()).toString()
                val cfg = AlarmPreferences.update(ctx) {
                    copy(
                        minIntervalMin = minInterval,
                        maxIntervalMin = maxInterval,
                        adjustmentPercent = adjustmentPercent,
                        overrideTodayDate = if (includeTodayOverride) todayStr else null
                    )
                }
                toggleActiveWithConfig(cfg, includeTodayOverride)
            }
        }
    }

    fun startForToday() {
        viewModelScope.launch {
            val zone = java.time.ZoneId.systemDefault()
            val now = java.time.ZonedDateTime.now(zone)
            val today = now.toLocalDate()
            val cfg = config.value
            val todayStart = today.atTime(cfg.startHour, cfg.startMinute).atZone(zone)
            val todayEnd = today.atTime(cfg.endHour, cfg.endMinute).atZone(zone)
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
                return@launch
            }
            val updated = AlarmPreferences.update(ctx) {
                copy(isActive = true, overrideTodayDate = today.toString())
            }
            AlarmScheduler.schedule(ctx, updated)
        }
    }

    private suspend fun toggleActiveWithConfig(cfg: AlarmConfig, includeTodayOverride: Boolean = false) {
        if (cfg.isActive) {
            AlarmScheduler.cancel(ctx, clearPreference = false)
            AlarmPreferences.setActive(ctx, false)
            stopFiringAlarmIfNeeded()
        } else {
            if (cfg.windowMinutes < cfg.minIntervalMin) return
            val zone = java.time.ZoneId.systemDefault()
            val now = java.time.ZonedDateTime.now(zone)
            val today = now.toLocalDate()
            val todayStart = today.atTime(cfg.startHour, cfg.startMinute).atZone(zone)
            val todayEnd = today.atTime(cfg.endHour, cfg.endMinute).atZone(zone)
            val minIntervalMs = cfg.minIntervalMin * 60_000L
            val remainingMsToday = if (now.isBefore(todayStart)) {
                java.time.Duration.between(todayStart, todayEnd).toMillis()
            } else if (now.isBefore(todayEnd)) {
                java.time.Duration.between(now, todayEnd).toMillis()
            } else {
                0L
            }

            val shouldCheckToday = cfg.repeatDays.isEmpty() ||
                cfg.repeatDays.contains(today.dayOfWeek.value) ||
                includeTodayOverride

            if (shouldCheckToday && remainingMsToday < minIntervalMs) {
                android.widget.Toast.makeText(
                    ctx,
                    ctx.getString(com.example.intervalalarm.R.string.msg_not_enough_time_today),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                if (cfg.repeatDays.isEmpty()) {
                    return
                }
            }
            val activeCfg = cfg.copy(
                isActive = true,
                overrideTodayDate = if (includeTodayOverride && remainingMsToday >= minIntervalMs) today.toString() else null
            )
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
                ctx.startService(intent)
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
