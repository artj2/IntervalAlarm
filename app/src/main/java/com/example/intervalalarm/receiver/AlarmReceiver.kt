package com.example.intervalalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.intervalalarm.service.AlarmFiringService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        val serviceIntent = Intent(ctx, AlarmFiringService::class.java)
        ctx.startForegroundService(serviceIntent)
    }
}
