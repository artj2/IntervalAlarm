package com.example.intervalalarm.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.CombinedVibration
import android.os.IBinder
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.intervalalarm.IntervalAlarmApp
import com.example.intervalalarm.MainActivity
import com.example.intervalalarm.alarm.AlarmScheduler
import com.example.intervalalarm.data.AlarmHistoryDatabase
import com.example.intervalalarm.data.AlarmHistoryEntry
import com.example.intervalalarm.data.AlarmPreferences
import com.example.intervalalarm.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

class AlarmFiringService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null
    private var vibratorManager: VibratorManager? = null
    private var firingInitJob: Job? = null
    private var soundTimeoutJob: Job? = null
    private var vibrationTimeoutJob: Job? = null
    private val firingStarted = AtomicBoolean(false)
    private val actionHandled = AtomicBoolean(false)
    private lateinit var attributedContext: Context

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        attributedContext = createAttributionContext("alarm_firing")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(
            NOTIF_ID_SERVICE,
            NotificationCompat.Builder(this, IntervalAlarmApp.CHANNEL_SERVICE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(getString(R.string.notif_firing_title))
                .setSilent(true)
                .build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        when (intent?.action) {
            ACTION_DISMISS -> {
                if (firingStarted.get() || _isFiring.value) {
                    dismissAlarm()
                } else {
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_ACCEPT -> {
                if (firingStarted.get() || _isFiring.value) {
                    acceptAlarm()
                } else {
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }

        if (!firingStarted.compareAndSet(false, true)) {
            return START_NOT_STICKY
        }

        actionHandled.set(false)
        currentHistoryId = -1L

        firingInitJob = scope.launch {
            try {
                val cfg = AlarmPreferences.configFlow(this@AlarmFiringService).first()
                if (!cfg.isActive || actionHandled.get()) {
                    if (!actionHandled.get()) {
                        stopSelf()
                    }
                    return@launch
                }
                AlarmPreferences.setNextTriggerMillis(this@AlarmFiringService, null)

                currentHistoryId = AlarmHistoryDatabase.get(this@AlarmFiringService).dao()
                    .insert(AlarmHistoryEntry(status = "FIRED", initialTimeAloneSeconds = cfg.timeAloneSeconds))

                if (actionHandled.get()) return@launch
                _isFiring.value = true

                showAlarmNotification()

                if (cfg.vibrationEnabled && !actionHandled.get()) {
                    startVibration()
                    vibrationTimeoutJob?.cancel()
                    vibrationTimeoutJob = scope.launch {
                        delay(cfg.vibrationDurationSeconds.coerceAtLeast(1L) * 1000L)
                        stopVibrationOnly()
                    }
                }

                if (cfg.soundEnabled && !actionHandled.get()) {
                    startSound()
                    soundTimeoutJob?.cancel()
                    soundTimeoutJob = scope.launch {
                        delay(cfg.soundDurationSeconds.coerceAtLeast(1L) * 1000L)
                        stopSoundOnly()
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("AlarmFiring", "Error firing alarm", e)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun showAlarmNotification() {
        val tapIntent = PendingIntent.getActivity(
            this, REQ_TAP_ALARM,
            Intent(this, MainActivity::class.java).apply {
                putExtra("navigate_to_timer", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = PendingIntent.getService(
            this, REQ_DISMISS_ALARM,
            Intent(this, AlarmFiringService::class.java).apply {
                action = ACTION_DISMISS
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptIntent = PendingIntent.getActivity(
            this, REQ_ACCEPT_ALARM,
            Intent(this, MainActivity::class.java).apply {
                action = ACTION_ACCEPT_FROM_NOTIF
                putExtra("navigate_to_timer", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, IntervalAlarmApp.CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.notif_alarm_title))
            .setContentText(getString(R.string.notif_alarm_text))
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.btn_dismiss),
                dismissIntent
            )
            .addAction(
                android.R.drawable.ic_input_add,
                getString(R.string.btn_accept),
                acceptIntent
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID_ALARM, notif)
    }

    @Synchronized
    private fun startVibration() {
        stopVibrationOnly()
        val vm = attributedContext.getSystemService(VibratorManager::class.java)
        vibratorManager = vm
        val effect = VibrationEffect.createWaveform(
            longArrayOf(0, 400, 200, 400, 200, 400, 600),
            0
        )
        val combined = CombinedVibration.createParallel(effect)
        val attrs = VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_ALARM)
            .build()
        vm.vibrate(combined, attrs)
    }

    @Synchronized
    private fun startSound() {
        stopSoundOnly()
        val uri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(attributedContext, uri)
                isLooping = true
                setOnErrorListener { _, what, extra ->
                    android.util.Log.e("AlarmFiring", "MediaPlayer error: what=$what extra=$extra")
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            android.util.Log.e("AlarmFiring", "Error playing sound", e)
        }
    }

    private fun dismissAlarm() {
        if (!actionHandled.compareAndSet(false, true)) return
        _isFiring.value = false
        stopAlarmSoundOnly()
        scope.launch {
            try {
                firingInitJob?.join()
                stopAlarmSoundOnly()
                updateHistory(currentHistoryId, "DISMISSED")
                val cfg = AlarmPreferences.configFlow(this@AlarmFiringService).first()
                if (cfg.isActive) {
                    AlarmScheduler.schedule(this@AlarmFiringService, cfg)
                }
            } finally {
                stopSelf()
            }
        }
    }

    private fun acceptAlarm() {
        if (!actionHandled.compareAndSet(false, true)) return
        _isFiring.value = false
        stopAlarmSoundOnly()
        scope.launch {
            try {
                firingInitJob?.join()
                stopAlarmSoundOnly()
                val historyId = currentHistoryId
                val cfg = AlarmPreferences.configFlow(this@AlarmFiringService).first()
                val intent = Intent(this@AlarmFiringService, TimerService::class.java).apply {
                    action = TimerService.ACTION_START
                    putExtra(TimerService.EXTRA_SECONDS, cfg.timeAloneSeconds)
                    putExtra(TimerService.EXTRA_HISTORY_ID, historyId)
                    putExtra(TimerService.EXTRA_ALMOST_DONE_SECONDS, cfg.almostDoneSeconds)
                }
                startForegroundService(intent)
            } finally {
                stopSelf()
            }
        }
    }

    @Synchronized
    private fun stopSoundOnly() {
        mediaPlayer?.let {
            try {
                it.setOnCompletionListener(null)
                it.setOnErrorListener(null)
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            } catch (e: Exception) {
                android.util.Log.e("AlarmFiring", "Error stopping MediaPlayer", e)
            }
        }
        mediaPlayer = null
    }

    @Synchronized
    private fun stopVibrationOnly() {
        vibratorManager?.cancel()
        vibratorManager = null
    }

    private fun stopAlarmSoundOnly() {
        soundTimeoutJob?.cancel()
        soundTimeoutJob = null
        vibrationTimeoutJob?.cancel()
        vibrationTimeoutJob = null

        stopSoundOnly()
        stopVibrationOnly()

        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_ID_ALARM)
    }

    private suspend fun updateHistory(historyId: Long, status: String) {
        if (historyId == -1L) return
        val db = AlarmHistoryDatabase.get(this)
        db.dao().updateStatus(historyId, status)
    }

    override fun onDestroy() {
        soundTimeoutJob?.cancel()
        soundTimeoutJob = null
        vibrationTimeoutJob?.cancel()
        vibrationTimeoutJob = null
        stopSoundOnly()
        stopVibrationOnly()
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_ID_ALARM)
        currentHistoryId = -1L
        firingStarted.set(false)
        _isFiring.value = false
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_DISMISS = "com.example.intervalalarm.DISMISS_ALARM"
        const val ACTION_ACCEPT = "com.example.intervalalarm.ACCEPT_ALARM"
        const val ACTION_ACCEPT_FROM_NOTIF = "com.example.intervalalarm.ACTION_ACCEPT_FROM_NOTIF"
        private const val NOTIF_ID_SERVICE = 9001
        private const val NOTIF_ID_ALARM = 9002
        private const val REQ_TAP_ALARM = 100
        private const val REQ_DISMISS_ALARM = 101
        private const val REQ_ACCEPT_ALARM = 102

        private var currentHistoryId: Long = -1L

        private val _isFiring = MutableStateFlow(false)
        val isFiring = _isFiring.asStateFlow()
    }
}
