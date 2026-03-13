package com.example.intervalalarm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.intervalalarm.alarm.AlarmScheduler
import com.example.intervalalarm.data.AlarmHistoryDatabase
import com.example.intervalalarm.data.AlarmHistoryEntry
import com.example.intervalalarm.data.AlarmPreferences
import com.example.intervalalarm.model.AlarmConfig
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

    fun clearHistory() {
        viewModelScope.launch { historyDao.clearAll() }
    }
}
