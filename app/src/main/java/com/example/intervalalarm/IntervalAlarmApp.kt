package com.example.intervalalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class IntervalAlarmApp : Application() {

    companion object {
        const val CHANNEL_ALARM = "interval_alarm_channel"
        const val CHANNEL_SERVICE = "alarm_service_channel"
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM,
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                "Alarm Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
    }
}
