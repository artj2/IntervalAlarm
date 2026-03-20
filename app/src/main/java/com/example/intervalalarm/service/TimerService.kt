package com.example.intervalalarm.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
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
    private var decisionJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val initialSeconds = intent.getLongExtra(EXTRA_SECONDS, 60L)
                val historyId = intent.getLongExtra(EXTRA_HISTORY_ID, -1L)
                acquireWakeLock()
                startTimer(initialSeconds, historyId)
            }
            ACTION_FAILED -> {
                if (_isFinished.value) {
                    finalActionFailure()
                } else {
                    failTimerEarly()
                }
            }
            ACTION_SUCCESS -> {
                finalActionSuccess()
            }
        }
        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IntervalAlarm:TimerWakeLock").apply {
            acquire(3600_000L) // 1 hour max
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun startTimer(seconds: Long, historyId: Long) {
        _remainingSeconds.value = seconds
        _initialSeconds.value = seconds
        currentHistoryId = historyId
        _isFinished.value = false
        _decisionTimerSeconds.value = 10

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
            startDecisionPhase()
        }
    }

    private fun startDecisionPhase() {
        _isFinished.value = true
        decisionJob?.cancel()
        decisionJob = scope.launch {
            while (_decisionTimerSeconds.value > 0) {
                delay(1000)
                _decisionTimerSeconds.value -= 1
            }
            finalActionSuccess()
        }
    }

    private fun showCompletionNotification(isSuccess: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 3, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isSuccess) getString(R.string.msg_congrats) else "Time Alone Failed"
        val text = if (isSuccess) getString(R.string.btn_success) else "The session has ended."

        val notif = NotificationCompat.Builder(this, IntervalAlarmApp.CHANNEL_TIMER_ALERTS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_ALERT, notif)
    }

    private fun finalActionSuccess() {
        decisionJob?.cancel()
        releaseWakeLock()
        scope.launch {
            updateHistory("SUCCESS", _initialSeconds.value)
            AlarmPreferences.updateTimeAlone(this@TimerService, true)
            resumeIntervals()
            showCompletionNotification(true)
            stopSelf()
        }
    }

    private fun finalActionFailure() {
        decisionJob?.cancel()
        releaseWakeLock()
        scope.launch {
            updateHistory("FAILED", _initialSeconds.value)
            AlarmPreferences.updateTimeAlone(this@TimerService, false)
            resumeIntervals()
            showCompletionNotification(false)
            stopSelf()
        }
    }

    private fun failTimerEarly() {
        timerJob?.cancel()
        releaseWakeLock()
        val elapsed = _initialSeconds.value - _remainingSeconds.value
        scope.launch {
            updateHistory("FAILED", elapsed)
            AlarmPreferences.updateTimeAlone(this@TimerService, false)
            resumeIntervals()
            stopSelf()
        }
    }

    private suspend fun resumeIntervals() {
        val cfg = AlarmPreferences.configFlow(this@TimerService).first()
        if (cfg.isActive) {
            AlarmScheduler.schedule(this@TimerService, cfg)
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
        decisionJob?.cancel()
        releaseWakeLock()
        scope.cancel()
        _isFinished.value = false
        _remainingSeconds.value = 0
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.intervalalarm.START_TIMER"
        const val ACTION_FAILED = "com.example.intervalalarm.FAILED_TIMER"
        const val ACTION_SUCCESS = "com.example.intervalalarm.SUCCESS_TIMER"
        const val EXTRA_SECONDS = "extra_seconds"
        const val EXTRA_HISTORY_ID = "extra_history_id"
        private const val NOTIF_ID = 9003
        private const val NOTIF_ID_ALERT = 9004

        private val _remainingSeconds = MutableStateFlow(0L)
        val remainingSeconds = _remainingSeconds.asStateFlow()

        private val _initialSeconds = MutableStateFlow(0L)
        val initialSeconds = _initialSeconds.asStateFlow()

        private val _isFinished = MutableStateFlow(false)
        val isFinished = _isFinished.asStateFlow()

        private val _decisionTimerSeconds = MutableStateFlow(10)
        val decisionTimerSeconds = _decisionTimerSeconds.asStateFlow()

        var currentHistoryId: Long = -1L

        fun stop(ctx: android.content.Context) {
            ctx.stopService(Intent(ctx, TimerService::class.java))
            _isFinished.value = false
        }
    }
}
