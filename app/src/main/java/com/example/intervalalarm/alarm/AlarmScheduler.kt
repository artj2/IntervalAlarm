package com.example.intervalalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.intervalalarm.model.AlarmConfig
import com.example.intervalalarm.receiver.AlarmReceiver
import java.time.*
import kotlin.random.Random

object AlarmScheduler {

    private const val REQ_CODE = 1001

    fun nextTriggerTime(cfg: AlarmConfig, fromMillis: Long = System.currentTimeMillis()): Long? {
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(fromMillis).atZone(zone)

        val intervalMs = Random.nextLong(
            cfg.minIntervalMin * 60_000L,
            cfg.maxIntervalMin * 60_000L + 1
        )
        val proposedInstant = fromMillis + intervalMs
        val proposed = Instant.ofEpochMilli(proposedInstant).atZone(zone)

        val todayStart = now.toLocalDate().atTime(cfg.startHour, cfg.startMinute).atZone(zone)
        val todayEnd = now.toLocalDate().atTime(cfg.endHour, cfg.endMinute).atZone(zone)

        if (proposed.isAfter(todayStart) && proposed.isBefore(todayEnd)) {
            return proposedInstant
        }

        return nextWindowStart(cfg, now)
    }

    fun nextWindowStart(cfg: AlarmConfig, from: ZonedDateTime = ZonedDateTime.now()): Long? {
        val zone = from.zone
        val todayStart = from.toLocalDate().atTime(cfg.startHour, cfg.startMinute).atZone(zone)

        if (from.isBefore(todayStart)) {
            val todayDow = from.dayOfWeek.value
            if (cfg.repeatDays.isEmpty() || cfg.repeatDays.contains(todayDow)) {
                val offsetMs = Random.nextLong(0, cfg.minIntervalMin * 60_000L + 1)
                return todayStart.toInstant().toEpochMilli() + offsetMs
            }
        }

        // Check subsequent days
        for (d in 1..7) {
            val candidateDate = from.toLocalDate().plusDays(d.toLong())
            val candidateDow = candidateDate.dayOfWeek.value
            if (cfg.repeatDays.isEmpty() || cfg.repeatDays.contains(candidateDow)) {
                val start = candidateDate.atTime(cfg.startHour, cfg.startMinute).atZone(zone)
                val offsetMs = Random.nextLong(0, cfg.minIntervalMin * 60_000L + 1)
                return start.toInstant().toEpochMilli() + offsetMs
            }
        }

        return null
    }

    fun schedule(ctx: Context, cfg: AlarmConfig) {
        val triggerAt = nextTriggerTime(cfg) ?: run {
            cancel(ctx)
            return
        }

        val am = ctx.getSystemService(AlarmManager::class.java)
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                makePendingIntent(ctx)
            )
        } else {
            // Fallback or log warning. Since USE_EXACT_ALARM is in manifest,
            // this usually shouldn't happen unless restricted by battery saver/system.
            android.util.Log.w("AlarmScheduler", "Cannot schedule exact alarms")
        }
    }

    fun scheduleNextWindow(ctx: Context, cfg: AlarmConfig) {
        val next = nextWindowStart(cfg) ?: run {
            cancel(ctx)
            return
        }

        val am = ctx.getSystemService(AlarmManager::class.java)
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                next,
                makePendingIntent(ctx)
            )
        } else {
            android.util.Log.w("AlarmScheduler", "Cannot schedule exact alarms")
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(makePendingIntent(ctx))
    }

    private fun makePendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            ctx, REQ_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
