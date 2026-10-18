package com.example.intervalalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.intervalalarm.R
import com.example.intervalalarm.data.AlarmPreferences
import com.example.intervalalarm.model.AlarmConfig
import com.example.intervalalarm.receiver.AlarmReceiver
import java.time.*
import java.time.format.TextStyle
import java.util.Locale
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

    suspend fun schedule(ctx: Context, cfg: AlarmConfig): Long? {
        val triggerAt = nextTriggerTime(cfg) ?: run {
            cancel(ctx)
            return null
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
        AlarmPreferences.setNextTriggerMillis(ctx, triggerAt)
        return triggerAt
    }

    suspend fun scheduleNextWindow(ctx: Context, cfg: AlarmConfig) {
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
        AlarmPreferences.setNextTriggerMillis(ctx, next)
    }

    suspend fun cancel(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(makePendingIntent(ctx))
        AlarmPreferences.setNextTriggerMillis(ctx, null)
    }

    fun formatNextTrigger(ctx: Context, triggerMillis: Long?, nowMillis: Long = System.currentTimeMillis()): String {
        if (triggerMillis == null || triggerMillis <= 0L) {
            return ctx.getString(R.string.next_none_scheduled)
        }
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val trigger = Instant.ofEpochMilli(triggerMillis).atZone(zone)

        val timeStr = "%02d:%02d".format(trigger.hour, trigger.minute)
        val diffMs = triggerMillis - nowMillis
        val diffMinutes = diffMs / 60_000L
        val diffHours = diffMs / 3_600_000L
        val remainingMins = (diffMs % 3_600_000L) / 60_000L

        val nowDate = now.toLocalDate()
        val triggerDate = trigger.toLocalDate()

        val datePrefix = when {
            triggerDate == nowDate -> timeStr
            triggerDate == nowDate.plusDays(1) -> ctx.getString(R.string.time_tomorrow_at, timeStr)
            else -> ctx.getString(
                R.string.time_day_at,
                trigger.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                timeStr
            )
        }

        if (diffMs <= 0L) {
            return ctx.getString(R.string.next_due_now, datePrefix)
        }

        val relStr = when {
            diffMinutes < 1L -> "< 1m"
            diffHours < 1L -> "${diffMinutes}m"
            diffHours < 24L -> if (remainingMins > 0L) "${diffHours}h ${remainingMins}m" else "${diffHours}h"
            else -> {
                val days = diffHours / 24L
                val remHours = diffHours % 24L
                if (remHours > 0L) "${days}d ${remHours}h" else "${days}d"
            }
        }

        return ctx.getString(R.string.next_in_format, datePrefix, relStr)
    }

    private fun makePendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            ctx, REQ_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
