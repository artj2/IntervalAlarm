package com.example.intervalalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.intervalalarm.alarm.AlarmScheduler
import com.example.intervalalarm.data.AlarmPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cfg = AlarmPreferences.configFlow(ctx).first()
                if (cfg.isActive) {
                    AlarmScheduler.schedule(ctx, cfg)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
