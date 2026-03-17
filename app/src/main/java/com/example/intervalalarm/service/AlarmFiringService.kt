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
import androidx.core.net.toUri
import com.example.intervalalarm.IntervalAlarmApp
import com.example.intervalalarm.MainActivity
import com.example.intervalalarm.alarm.AlarmScheduler
import com.example.intervalalarm.data.AlarmHistoryDatabase
import com.example.intervalalarm.data.AlarmHistoryEntry
import com.example.intervalalarm.data.AlarmPreferences
import com.example.intervalalarm.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class AlarmFiringService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null
    private var vibratorManager: VibratorManager? = null
    private lateinit var attributedContext: Context

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        attributedContext = createAttributionContext("alarm_firing")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> {
                dismissAlarm()
                return START_NOT_STICKY
            }
            ACTION_ACCEPT -> {
                acceptAlarm()
                return START_NOT_STICKY
            }
        }

        startForeground(
            NOTIF_ID_SERVICE,
            NotificationCompat.Builder(this, IntervalAlarmApp.CHANNEL_SERVICE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(getString(R.string.notif_firing_title))
                .setSilent(true)
                .build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )

        scope.launch {
            try {
                val cfg = AlarmPreferences.configFlow(this@AlarmFiringService).first()
                if (!cfg.isActive) {
                    stopSelf()
                    return@launch
                }

                currentHistoryId = AlarmHistoryDatabase.get(this@AlarmFiringService).dao()
                    .insert(AlarmHistoryEntry(status = "FIRED", initialTimeAloneSeconds = cfg.timeAloneSeconds))

                if (cfg.notificationEnabled) {
                    showAlarmNotification()
                }

                if (cfg.vibrationEnabled) {
                    startVibration()
                }

                if (cfg.soundEnabled) {
                    startSound(cfg.soundUri)
                }

                if (!cfg.notificationEnabled && !cfg.soundEnabled && !cfg.vibrationEnabled) {
                    scheduleNextAndStop()
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
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("navigate_to_timer", true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val dismissIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AlarmFiringService::class.java).apply {
                action = ACTION_DISMISS
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val acceptIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).apply {
                action = "com.example.intervalalarm.ACTION_ACCEPT_FROM_NOTIF"
                putExtra("navigate_to_timer", true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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

    private fun startVibration() {
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

    private fun startSound(uriStr: String?) {
        val uri: Uri = uriStr?.toUri()
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
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
        stopAlarmSoundOnly()
        scope.launch {
            try {
                updateHistory("DISMISSED")
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
        stopAlarmSoundOnly()
        scope.launch {
            try {
                val cfg = AlarmPreferences.configFlow(this@AlarmFiringService).first()
                val intent = Intent(this@AlarmFiringService, TimerService::class.java).apply {
                    action = TimerService.ACTION_START
                    putExtra(TimerService.EXTRA_SECONDS, cfg.timeAloneSeconds)
                    putExtra(TimerService.EXTRA_HISTORY_ID, currentHistoryId)
                }
                startForegroundService(intent)
            } finally {
                stopSelf()
            }
        }
    }

    private fun stopAlarmSoundOnly() {
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

        vibratorManager?.cancel()
        vibratorManager = null

        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_ID_ALARM)
    }

    private fun scheduleNextAndStop() {
        scope.launch {
            try {
                updateHistory("DISMISSED")
                val cfg = AlarmPreferences.configFlow(this@AlarmFiringService).first()
                if (cfg.isActive) {
                    AlarmScheduler.schedule(this@AlarmFiringService, cfg)
                }
            } finally {
                stopSelf()
            }
        }
    }

    private suspend fun updateHistory(status: String) {
        if (currentHistoryId == -1L) return
        val db = AlarmHistoryDatabase.get(this)
        val entry = AlarmHistoryEntry(id = currentHistoryId, status = status)
        db.dao().update(entry)
    }

    override fun onDestroy() {
        mediaPlayer?.let {
            try {
                it.setOnCompletionListener(null)
                it.setOnErrorListener(null)
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            } catch (_: Exception) {}
        }
        mediaPlayer = null
        vibratorManager?.cancel()
        vibratorManager = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_DISMISS = "com.example.intervalalarm.DISMISS_ALARM"
        const val ACTION_ACCEPT = "com.example.intervalalarm.ACCEPT_ALARM"
        private const val NOTIF_ID_SERVICE = 9001
        private const val NOTIF_ID_ALARM = 9002

        private var currentHistoryId: Long = -1L
    }
}
