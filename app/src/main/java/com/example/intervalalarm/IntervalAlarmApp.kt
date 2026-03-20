package com.example.intervalalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class IntervalAlarmApp : Application() {

    companion object {
        const val CHANNEL_ALARM = "interval_alarm_channel"
        const val CHANNEL_SERVICE = "alarm_service_channel"
        const val CHANNEL_TIMER = "timer_service_channel"
        const val CHANNEL_TIMER_ALERTS = "timer_alerts_channel"
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM,
                getString(R.string.channel_alarm_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TIMER,
                getString(R.string.channel_timer_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TIMER_ALERTS,
                "Timer Completion Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300)
            }
        )
    }
}
