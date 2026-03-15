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
        // Handle dismiss action
        if (intent?.action == ACTION_DISMISS) {
            stopAlarm()
            return START_NOT_STICKY
        }

        // Immediately go foreground
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

                // Log to history
                AlarmHistoryDatabase.get(this@AlarmFiringService).dao()
                    .insert(AlarmHistoryEntry())

                // Show alarm notification with dismiss button
                if (cfg.notificationEnabled) {
                    showAlarmNotification()
                }

                // Start vibration (repeating)
                if (cfg.vibrationEnabled) {
                    startVibration()
                }

                // Start sound (looping)
                if (cfg.soundEnabled) {
                    startSound(cfg.soundUri)
                }

                // If all alerts are off, just schedule next
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
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = PendingIntent.getService(
            this, 1,
            Intent(this, AlarmFiringService::class.java).apply {
                action = ACTION_DISMISS
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
            0 // repeat from index 0
        )
        val combined = CombinedVibration.createParallel(effect)
        val attrs = VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_ALARM)
            .build()
        vm.vibrate(combined, attrs)
    }

    private fun startSound(uriStr: String?) {
        val uri: Uri = if (uriStr != null) {
            uriStr.toUri()
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

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

    private fun stopAlarm() {
        // Stop sound safely
        try {
            mediaPlayer?.let {
                it.setOnCompletionListener(null)
                it.setOnErrorListener(null)
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            android.util.Log.e("AlarmFiring", "Error stopping MediaPlayer", e)
        }
        mediaPlayer = null

        // Stop vibration
        vibratorManager?.cancel()
        vibratorManager = null

        // Clear notification
        val nm = getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_ID_ALARM)

        // Schedule next alarm
        scope.launch {
            try {
                val cfg = AlarmPreferences.configFlow(this@AlarmFiringService).first()
                if (cfg.isActive) {
                    AlarmScheduler.schedule(this@AlarmFiringService, cfg)
                }
            } finally {
                stopSelf()
            }
        }
    }

    private fun scheduleNextAndStop() {
        scope.launch {
            try {
                val cfg = AlarmPreferences.configFlow(this@AlarmFiringService).first()
                if (cfg.isActive) {
                    AlarmScheduler.schedule(this@AlarmFiringService, cfg)
                }
            } finally {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        try {
            mediaPlayer?.let {
                it.setOnCompletionListener(null)
                it.setOnErrorListener(null)
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        vibratorManager?.cancel()
        vibratorManager = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_DISMISS = "com.example.intervalalarm.DISMISS_ALARM"
        private const val NOTIF_ID_SERVICE = 9001
        private const val NOTIF_ID_ALARM = 9002
    }
}