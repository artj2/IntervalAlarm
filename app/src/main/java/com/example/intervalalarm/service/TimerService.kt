package com.example.intervalalarm.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
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
    private var almostDoneAlertShown = false
    private var almostDoneThresholdSeconds: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val initialSeconds = intent.getLongExtra(EXTRA_SECONDS, 60L)
                val historyId = intent.getLongExtra(EXTRA_HISTORY_ID, -1L)
                val almostDoneSeconds = intent.getLongExtra(EXTRA_ALMOST_DONE_SECONDS, 60L)
                acquireWakeLock(initialSeconds)
                startTimer(initialSeconds, historyId, almostDoneSeconds)
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

    private fun acquireWakeLock(durationSeconds: Long) {
        releaseWakeLock()
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val timeoutMs = (durationSeconds + 60L) * 1000L
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IntervalAlarm:TimerWakeLock").apply {
                setReferenceCounted(false)
                acquire(timeoutMs)
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun startTimer(seconds: Long, historyId: Long, almostDoneSeconds: Long = 60L) {
        _remainingSeconds.value = seconds
        _initialSeconds.value = seconds
        currentHistoryId = historyId
        _isFinished.value = false
        _decisionTimerSeconds.value = 10
        almostDoneAlertShown = false
        almostDoneThresholdSeconds = almostDoneSeconds

        startForeground(
            NOTIF_ID,
            buildNotification(seconds),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        val endTimeRealtime = SystemClock.elapsedRealtime() + seconds * 1000L

        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                val remainingMs = endTimeRealtime - SystemClock.elapsedRealtime()
                if (remainingMs <= 0L) {
                    _remainingSeconds.value = 0L
                    updateNotification(0L)
                    break
                }
                val remainingSec = (remainingMs + 999L) / 1000L
                if (_remainingSeconds.value != remainingSec) {
                    _remainingSeconds.value = remainingSec
                    updateNotification(remainingSec)

                    if (!almostDoneAlertShown &&
                        almostDoneThresholdSeconds > 0 &&
                        _initialSeconds.value > almostDoneThresholdSeconds &&
                        remainingSec <= almostDoneThresholdSeconds
                    ) {
                        almostDoneAlertShown = true
                        showAlmostDoneNotification()
                    }
                }
                val delayTime = (remainingMs % 1000L).let { if (it == 0L) 1000L else it }
                delay(delayTime.coerceIn(100L, 1000L))
            }
            startDecisionPhase()
        }
    }

    private fun showAlmostDoneNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 4, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, IntervalAlarmApp.CHANNEL_TIMER_ALERTS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.notif_alarm_title))
            .setContentText(getString(R.string.notif_almost_done_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_ALMOST_DONE, notif)

        try {
            val vm = getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (_: Exception) {}
    }

    private fun startDecisionPhase() {
        _isFinished.value = true
        val decisionEndTimeRealtime = SystemClock.elapsedRealtime() + 10_000L
        decisionJob?.cancel()
        decisionJob = scope.launch {
            while (true) {
                val remainingMs = decisionEndTimeRealtime - SystemClock.elapsedRealtime()
                if (remainingMs <= 0L) {
                    _decisionTimerSeconds.value = 0
                    break
                }
                val sec = ((remainingMs + 999L) / 1000L).toInt()
                _decisionTimerSeconds.value = sec
                val delayTime = (remainingMs % 1000L).let { if (it == 0L) 1000L else it }
                delay(delayTime.coerceIn(100L, 1000L))
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
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_ID_ALMOST_DONE)
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
        const val EXTRA_ALMOST_DONE_SECONDS = "extra_almost_done_seconds"
        private const val NOTIF_ID = 9003
        private const val NOTIF_ID_ALERT = 9004
        private const val NOTIF_ID_ALMOST_DONE = 9005

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
