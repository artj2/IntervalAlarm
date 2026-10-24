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

data class NextTriggerDisplay(
    val timeText: String,
    val countdownText: String
)

object AlarmScheduler {

    private const val REQ_CODE = 1001

    fun isDateAllowed(date: LocalDate, cfg: AlarmConfig): Boolean {
        return cfg.repeatDays.isEmpty() ||
            cfg.repeatDays.contains(date.dayOfWeek.value) ||
            cfg.overrideTodayDate == date.toString()
    }

    fun nextTriggerTime(cfg: AlarmConfig, fromMillis: Long = System.currentTimeMillis()): Long? {
        val zone = ZoneId.systemDefault()
        val now = Instant.ofEpochMilli(fromMillis).atZone(zone)
        val today = now.toLocalDate()

        if (isDateAllowed(today, cfg)) {
            val todayStart = today.atTime(cfg.startHour, cfg.startMinute).atZone(zone)
            val todayEnd = today.atTime(cfg.endHour, cfg.endMinute).atZone(zone)

            if (now.isBefore(todayStart)) {
                return nextWindowStart(cfg, now)
            }

            if (now.isBefore(todayEnd)) {
                val remainingMs = todayEnd.toInstant().toEpochMilli() - fromMillis
                val minIntervalMs = cfg.minIntervalMin * 60_000L
                val maxIntervalMs = cfg.maxIntervalMin * 60_000L

                if (remainingMs >= minIntervalMs) {
                    val maxInterval = minOf(maxIntervalMs, remainingMs)
                    val intervalMs = Random.nextLong(minIntervalMs, maxInterval + 1)
                    val proposedInstant = fromMillis + intervalMs
                    if (proposedInstant < todayEnd.toInstant().toEpochMilli()) {
                        return proposedInstant
                    }
                }
            }
        }

        return nextWindowStart(cfg, now)
    }

    fun nextWindowStart(cfg: AlarmConfig, from: ZonedDateTime = ZonedDateTime.now()): Long? {
        val zone = from.zone
        val today = from.toLocalDate()
        val todayStart = today.atTime(cfg.startHour, cfg.startMinute).atZone(zone)
        val todayEnd = today.atTime(cfg.endHour, cfg.endMinute).atZone(zone)

        if (from.isBefore(todayStart) && isDateAllowed(today, cfg)) {
            val windowDurationMs = Duration.between(todayStart, todayEnd).toMillis()
            val maxOffsetMs = minOf(cfg.minIntervalMin * 60_000L, (windowDurationMs - 1L).coerceAtLeast(0L))
            val offsetMs = if (maxOffsetMs > 0L) Random.nextLong(0, maxOffsetMs + 1) else 0L
            return todayStart.toInstant().toEpochMilli() + offsetMs
        }

        // If repeatDays is empty, this is a manual single-day session only; do not roll over to future days.
        if (cfg.repeatDays.isEmpty()) {
            return null
        }

        // Check subsequent days
        for (d in 1..7) {
            val candidateDate = today.plusDays(d.toLong())
            val candidateDow = candidateDate.dayOfWeek.value
            if (cfg.repeatDays.contains(candidateDow)) {
                val start = candidateDate.atTime(cfg.startHour, cfg.startMinute).atZone(zone)
                val end = candidateDate.atTime(cfg.endHour, cfg.endMinute).atZone(zone)
                val windowDurationMs = Duration.between(start, end).toMillis()
                val maxOffsetMs = minOf(cfg.minIntervalMin * 60_000L, (windowDurationMs - 1L).coerceAtLeast(0L))
                val offsetMs = if (maxOffsetMs > 0L) Random.nextLong(0, maxOffsetMs + 1) else 0L
                return start.toInstant().toEpochMilli() + offsetMs
            }
        }

        return null
    }

    suspend fun schedule(ctx: Context, cfg: AlarmConfig): Long? {
        val triggerAt = nextTriggerTime(cfg) ?: run {
            cancel(ctx, clearPreference = false)
            AlarmPreferences.setActive(
                ctx,
                active = cfg.isActive && cfg.repeatDays.isNotEmpty(),
                nextTriggerMillis = null
            )
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
        AlarmPreferences.setActive(ctx, active = true, nextTriggerMillis = triggerAt)
        return triggerAt
    }

    suspend fun cancel(ctx: Context, clearPreference: Boolean = true) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        am.cancel(makePendingIntent(ctx))
        if (clearPreference) {
            AlarmPreferences.setNextTriggerMillis(ctx, null)
        }
    }

    fun getNextTriggerDisplay(ctx: Context, triggerMillis: Long?, nowMillis: Long = System.currentTimeMillis()): NextTriggerDisplay {
        if (triggerMillis == null || triggerMillis <= 0L) {
            return NextTriggerDisplay(
                timeText = ctx.getString(R.string.next_none_scheduled),
                countdownText = ""
            )
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

        val timeText = when {
            triggerDate == nowDate -> timeStr
            triggerDate == nowDate.plusDays(1) -> ctx.getString(R.string.time_tomorrow_at, timeStr)
            else -> ctx.getString(
                R.string.time_day_at,
                trigger.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                timeStr
            )
        }

        val countdownText = when {
            diffMs <= 0L -> ctx.getString(R.string.next_due_now)
            diffMinutes < 1L -> ctx.getString(R.string.next_less_than_min)
            diffHours < 1L -> if (diffMinutes == 1L) "in 1 minute" else "in $diffMinutes minutes"
            diffHours < 24L -> if (remainingMins > 0L) "in ${diffHours}h ${remainingMins}m" else if (diffHours == 1L) "in 1 hour" else "in $diffHours hours"
            else -> {
                val days = diffHours / 24L
                val remHours = diffHours % 24L
                if (remHours > 0L) "in ${days}d ${remHours}h" else if (days == 1L) "in 1 day" else "in $days days"
            }
        }

        return NextTriggerDisplay(timeText = timeText, countdownText = countdownText)
    }

    private fun makePendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            ctx, REQ_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
