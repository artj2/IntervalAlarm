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
import com.example.intervalalarm.data.PersistedTimerSession
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class TimerService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var timerJob: Job? = null
    private var decisionJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var almostDoneAlertShown = false
    private var almostDoneThresholdSeconds: Long = 0L
    private val outcomeHandled = AtomicBoolean(false)
    private val activeSessionToken = AtomicLong(0L)
    private val sessionMutex = Mutex()

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
            null -> {
                startForeground(
                    NOTIF_ID,
                    buildNotification(_remainingSeconds.value),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
                restoreSession()
            }
        }
        return START_STICKY
    }

    @Synchronized
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

    @Synchronized
    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun startTimer(seconds: Long, historyId: Long, almostDoneSeconds: Long = 60L) {
        val token = activeSessionToken.incrementAndGet()
        outcomeHandled.set(false)
        decisionJob?.cancel()
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

        val endEpochMillis = System.currentTimeMillis() + seconds * 1000L
        scope.launch {
            sessionMutex.withLock {
                if (activeSessionToken.get() == token) {
                    AlarmPreferences.saveTimerSession(
                        this@TimerService,
                        PersistedTimerSession(
                            endEpochMillis = endEpochMillis,
                            initialSeconds = seconds,
                            historyId = historyId,
                            almostDoneSeconds = almostDoneSeconds,
                            almostDoneShown = false,
                            decisionEndEpochMillis = 0L
                        )
                    )
                }
            }
        }

        val endTimeRealtime = SystemClock.elapsedRealtime() + seconds * 1000L
        runCountdownLoop(endTimeRealtime, token)
    }

    private fun restoreSession() {
        val token = activeSessionToken.incrementAndGet()
        scope.launch {
            val session = sessionMutex.withLock {
                if (activeSessionToken.get() != token) return@launch
                AlarmPreferences.getTimerSession(this@TimerService)
            }
            if (session == null) {
                if (activeSessionToken.get() == token) {
                    stopSelf()
                }
                return@launch
            }
            if (activeSessionToken.get() != token) return@launch

            outcomeHandled.set(false)
            _initialSeconds.value = session.initialSeconds
            currentHistoryId = session.historyId
            almostDoneThresholdSeconds = session.almostDoneSeconds
            almostDoneAlertShown = session.almostDoneShown

            val now = System.currentTimeMillis()
            val remainingMs = session.endEpochMillis - now
            if (remainingMs > 0L) {
                val remainingSec = (remainingMs + 999L) / 1000L
                _remainingSeconds.value = remainingSec
                _isFinished.value = false
                if (almostDoneThresholdSeconds > 0 && remainingSec <= almostDoneThresholdSeconds) {
                    almostDoneAlertShown = true
                }
                acquireWakeLock(remainingSec)
                updateNotification(remainingSec)
                runCountdownLoop(SystemClock.elapsedRealtime() + remainingMs, token)
            } else {
                _remainingSeconds.value = 0L
                updateNotification(0L)
                if (session.decisionEndEpochMillis == 0L) {
                    startDecisionPhase(durationMs = 10_000L, triggerAlert = true, token = token)
                } else {
                    val decisionRemMs = session.decisionEndEpochMillis - now
                    if (decisionRemMs > 0L) {
                        startDecisionPhase(durationMs = decisionRemMs, triggerAlert = false, token = token)
                    } else {
                        finalActionSuccess()
                    }
                }
            }
        }
    }

    private fun runCountdownLoop(endTimeRealtime: Long, token: Long) {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (true) {
                if (activeSessionToken.get() != token) return@launch
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
                        sessionMutex.withLock {
                            if (activeSessionToken.get() == token) {
                                AlarmPreferences.updateTimerSessionAlmostDoneShown(this@TimerService, true)
                            }
                        }
                        showAlmostDoneNotification()
                    }
                }
                val delayTime = (remainingMs % 1000L).let { if (it == 0L) 1000L else it }
                delay(delayTime.coerceIn(100L, 1000L))
            }
            if (activeSessionToken.get() == token) {
                startDecisionPhase(durationMs = 10_000L, triggerAlert = true, token = token)
            }
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

    private fun startDecisionPhase(
        durationMs: Long = 10_000L,
        triggerAlert: Boolean = true,
        token: Long = activeSessionToken.get()
    ) {
        _isFinished.value = true
        if (triggerAlert) {
            triggerCompletionAlert()
            val decisionEndEpoch = System.currentTimeMillis() + durationMs
            scope.launch {
                sessionMutex.withLock {
                    if (activeSessionToken.get() == token) {
                        AlarmPreferences.updateTimerSessionDecisionEnd(this@TimerService, decisionEndEpoch)
                    }
                }
            }
        } else {
            showDecisionNotification()
        }
        val decisionEndTimeRealtime = SystemClock.elapsedRealtime() + durationMs
        decisionJob?.cancel()
        decisionJob = scope.launch {
            while (true) {
                if (activeSessionToken.get() != token) return@launch
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
            if (activeSessionToken.get() == token) {
                finalActionSuccess()
            }
        }
    }

    private fun triggerCompletionAlert() {
        try {
            val vm = getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1)
            )
        } catch (_: Exception) {}

        showDecisionNotification()
    }

    private fun showDecisionNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 3, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val failIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_FAILED
        }
        val failPendingIntent = PendingIntent.getService(
            this, 10, failIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val successIntent = Intent(this, TimerService::class.java).apply {
            action = ACTION_SUCCESS
        }
        val successPendingIntent = PendingIntent.getService(
            this, 11, successIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, IntervalAlarmApp.CHANNEL_TIMER_ALERTS)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.msg_congrats))
            .setContentText(getString(R.string.notif_timer_complete_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, getString(R.string.btn_failed), failPendingIntent)
            .addAction(0, getString(R.string.btn_success), successPendingIntent)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_ALERT, notif)
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

    private suspend fun resolveSessionSnapshot(localHistoryId: Long, localInitial: Long): Pair<Long, Long> {
        var histId = localHistoryId
        var initSec = localInitial
        if (histId == -1L || initSec <= 0L) {
            val session = AlarmPreferences.getTimerSession(this@TimerService)
            if (session != null) {
                if (histId == -1L) histId = session.historyId
                if (initSec <= 0L) initSec = session.initialSeconds
            }
        }
        return histId to initSec
    }

    private fun finalActionSuccess() {
        if (!outcomeHandled.compareAndSet(false, true)) return
        val token = activeSessionToken.get()
        val localHistoryId = currentHistoryId
        val localInitial = _initialSeconds.value
        timerJob?.cancel()
        decisionJob?.cancel()
        _remainingSeconds.value = 0L
        _isFinished.value = false
        releaseWakeLock()
        scope.launch {
            sessionMutex.withLock {
                val (histId, initSec) = resolveSessionSnapshot(localHistoryId, localInitial)
                updateHistory(histId, "SUCCESS", initSec)
                AlarmPreferences.updateTimeAlone(this@TimerService, true)
                if (activeSessionToken.get() == token) {
                    AlarmPreferences.clearTimerSession(this@TimerService)
                    resumeIntervals()
                    showCompletionNotification(true)
                    stopSelf()
                }
            }
        }
    }

    private fun finalActionFailure() {
        if (!outcomeHandled.compareAndSet(false, true)) return
        val token = activeSessionToken.get()
        val localHistoryId = currentHistoryId
        val localInitial = _initialSeconds.value
        timerJob?.cancel()
        decisionJob?.cancel()
        _remainingSeconds.value = 0L
        _isFinished.value = false
        releaseWakeLock()
        scope.launch {
            sessionMutex.withLock {
                val (histId, initSec) = resolveSessionSnapshot(localHistoryId, localInitial)
                updateHistory(histId, "FAILED", initSec)
                AlarmPreferences.updateTimeAlone(this@TimerService, false)
                if (activeSessionToken.get() == token) {
                    AlarmPreferences.clearTimerSession(this@TimerService)
                    resumeIntervals()
                    showCompletionNotification(false)
                    stopSelf()
                }
            }
        }
    }

    private fun failTimerEarly() {
        if (!outcomeHandled.compareAndSet(false, true)) return
        val token = activeSessionToken.get()
        val localHistoryId = currentHistoryId
        val localInitial = _initialSeconds.value
        val localRemaining = _remainingSeconds.value
        timerJob?.cancel()
        decisionJob?.cancel()
        _remainingSeconds.value = 0L
        _isFinished.value = false
        releaseWakeLock()
        scope.launch {
            sessionMutex.withLock {
                val (histId, initSec) = resolveSessionSnapshot(localHistoryId, localInitial)
                val elapsed = (initSec - localRemaining).coerceAtLeast(0L)
                updateHistory(histId, "FAILED", elapsed)
                AlarmPreferences.updateTimeAlone(this@TimerService, false)
                if (activeSessionToken.get() == token) {
                    AlarmPreferences.clearTimerSession(this@TimerService)
                    resumeIntervals()
                    stopSelf()
                }
            }
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

    private suspend fun updateHistory(historyId: Long, status: String, elapsed: Long) {
        if (historyId == -1L) return
        val db = AlarmHistoryDatabase.get(this)
        db.dao().updateOutcome(historyId, status, elapsed)
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
    }
}
