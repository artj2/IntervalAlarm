package com.example.intervalalarm.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.intervalalarm.IntervalAlarmApp
import com.example.intervalalarm.MainActivity
import com.example.intervalalarm.R
import com.example.intervalalarm.alarm.AlarmScheduler
import com.example.intervalalarm.data.AlarmHistoryDatabase
import com.example.intervalalarm.data.AlarmHistoryEntry
import com.example.intervalalarm.data.AlarmPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class TimerService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var timerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val initialSeconds = intent.getLongExtra(EXTRA_SECONDS, 600L)
                val historyId = intent.getLongExtra(EXTRA_HISTORY_ID, -1L)
                startTimer(initialSeconds, historyId)
            }
            ACTION_FAILED -> {
                failTimer()
            }
        }
        return START_NOT_STICKY
    }

    private fun startTimer(seconds: Long, historyId: Long) {
        _remainingSeconds.value = seconds
        _initialSeconds.value = seconds
        currentHistoryId = historyId

        startForeground(
            NOTIF_ID,
            buildNotification(seconds),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        timerJob?.cancel()
        timerJob = scope.launch {
            while (_remainingSeconds.value > 0) {
                delay(1000)
                _remainingSeconds.value -= 1
                updateNotification(_remainingSeconds.value)
            }
            finishTimerSuccess()
        }
    }

    private fun updateNotification(seconds: Long) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(seconds))
    }

    private fun buildNotification(seconds: Long): android.app.Notification {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val timeStr = "%02d:%02d:%02d".format(h, m, s)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, IntervalAlarmApp.CHANNEL_TIMER)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.timer_running))
            .setContentText(getString(R.string.timer_remaining, timeStr))
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun finishTimerSuccess() {
        scope.launch {
            updateHistory("SUCCESS", _initialSeconds.value)
            AlarmPreferences.updateTimeAlone(this@TimerService, 1.1)
            val cfg = AlarmPreferences.configFlow(this@TimerService).first()
            if (cfg.isActive) {
                AlarmScheduler.schedule(this@TimerService, cfg)
            }
            _isFinished.value = true
            // We don't stopSelf yet to allow the UI to show the success message
            // The UI will stop the service when the user returns
        }
    }

    private fun failTimer() {
        timerJob?.cancel()
        val elapsed = _initialSeconds.value - _remainingSeconds.value
        scope.launch {
            updateHistory("FAILED", elapsed)
            AlarmPreferences.updateTimeAlone(this@TimerService, 0.9)
            val cfg = AlarmPreferences.configFlow(this@TimerService).first()
            if (cfg.isActive) {
                AlarmScheduler.schedule(this@TimerService, cfg)
            }
            stopSelf()
        }
    }

    private suspend fun updateHistory(status: String, elapsed: Long) {
        if (currentHistoryId == -1L) return
        val db = AlarmHistoryDatabase.get(this)
        val entry = AlarmHistoryEntry(
            id = currentHistoryId,
            status = status,
            initialTimeAloneSeconds = _initialSeconds.value,
            elapsedTimeSeconds = elapsed
        )
        db.dao().update(entry)
    }

    override fun onDestroy() {
        timerJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.intervalalarm.START_TIMER"
        const val ACTION_FAILED = "com.example.intervalalarm.FAILED_TIMER"
        const val EXTRA_SECONDS = "extra_seconds"
        const val EXTRA_HISTORY_ID = "extra_history_id"
        private const val NOTIF_ID = 9003

        private val _remainingSeconds = MutableStateFlow(0L)
        val remainingSeconds = _remainingSeconds.asStateFlow()

        private val _initialSeconds = MutableStateFlow(0L)
        val initialSeconds = _initialSeconds.asStateFlow()

        private val _isFinished = MutableStateFlow(false)
        val isFinished = _isFinished.asStateFlow()

        var currentHistoryId: Long = -1L

        fun stop(ctx: android.content.Context) {
            ctx.stopService(Intent(ctx, TimerService::class.java))
            _isFinished.value = false
        }
    }
}
