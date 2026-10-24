package com.example.intervalalarm.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.intervalalarm.model.AlarmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "alarm_prefs")

data class PersistedTimerSession(
    val endEpochMillis: Long,
    val initialSeconds: Long,
    val historyId: Long,
    val almostDoneSeconds: Long,
    val almostDoneShown: Boolean = false,
    val decisionEndEpochMillis: Long = 0L
)

object AlarmPreferences {

    private val START_HOUR = intPreferencesKey("start_hour")
    private val START_MIN = intPreferencesKey("start_min")
    private val END_HOUR = intPreferencesKey("end_hour")
    private val END_MIN = intPreferencesKey("end_min")
    private val MIN_INTERVAL = intPreferencesKey("min_interval")
    private val MAX_INTERVAL = intPreferencesKey("max_interval")
    private val SOUND_ON = booleanPreferencesKey("sound_on")
    private val VIB_ON = booleanPreferencesKey("vib_on")
    private val SOUND_DURATION_SECONDS = longPreferencesKey("sound_duration_seconds")
    private val VIB_DURATION_SECONDS = longPreferencesKey("vib_duration_seconds")
    private val IS_ACTIVE = booleanPreferencesKey("is_active")
    private val REPEAT_DAYS = stringPreferencesKey("repeat_days")
    private val TIME_ALONE = longPreferencesKey("time_alone")
    private val ADJUSTMENT_PERCENT = intPreferencesKey("adjustment_percent")
    private val ALMOST_DONE_SECONDS = longPreferencesKey("almost_done_seconds")
    private val NEXT_TRIGGER_MILLIS = longPreferencesKey("next_trigger_millis")

    private val SESSION_END_EPOCH = longPreferencesKey("session_end_epoch")
    private val SESSION_INITIAL_SEC = longPreferencesKey("session_initial_sec")
    private val SESSION_HISTORY_ID = longPreferencesKey("session_history_id")
    private val SESSION_ALMOST_DONE_SEC = longPreferencesKey("session_almost_done_sec")
    private val SESSION_ALMOST_DONE_SHOWN = booleanPreferencesKey("session_almost_done_shown")
    private val SESSION_DECISION_END_EPOCH = longPreferencesKey("session_decision_end_epoch")

    private fun fromPreferences(p: Preferences): AlarmConfig =
        AlarmConfig(
            startHour = p[START_HOUR] ?: 8,
            startMinute = p[START_MIN] ?: 0,
            endHour = p[END_HOUR] ?: 16,
            endMinute = p[END_MIN] ?: 30,
            minIntervalMin = p[MIN_INTERVAL] ?: 40,
            maxIntervalMin = p[MAX_INTERVAL] ?: 80,
            soundEnabled = p[SOUND_ON] ?: true,
            vibrationEnabled = p[VIB_ON] ?: true,
            soundDurationSeconds = p[SOUND_DURATION_SECONDS] ?: 5L,
            vibrationDurationSeconds = p[VIB_DURATION_SECONDS] ?: 2L,
            isActive = p[IS_ACTIVE] ?: false,
            repeatDays = p[REPEAT_DAYS]
                ?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.toSet()
                ?: emptySet(),
            timeAloneSeconds = p[TIME_ALONE] ?: 60L,
            adjustmentPercent = p[ADJUSTMENT_PERCENT] ?: 10,
            almostDoneSeconds = p[ALMOST_DONE_SECONDS] ?: 60L,
            nextTriggerMillis = p[NEXT_TRIGGER_MILLIS]?.takeIf { it > 0L }
        )

    private fun writeToPreferences(p: MutablePreferences, cfg: AlarmConfig) {
        p[START_HOUR] = cfg.startHour
        p[START_MIN] = cfg.startMinute
        p[END_HOUR] = cfg.endHour
        p[END_MIN] = cfg.endMinute
        p[MIN_INTERVAL] = cfg.minIntervalMin
        p[MAX_INTERVAL] = cfg.maxIntervalMin
        p[SOUND_ON] = cfg.soundEnabled
        p[VIB_ON] = cfg.vibrationEnabled
        p[SOUND_DURATION_SECONDS] = cfg.soundDurationSeconds
        p[VIB_DURATION_SECONDS] = cfg.vibrationDurationSeconds
        p[IS_ACTIVE] = cfg.isActive
        p[REPEAT_DAYS] = cfg.repeatDays.joinToString(",")
        p[TIME_ALONE] = cfg.timeAloneSeconds
        p[ADJUSTMENT_PERCENT] = cfg.adjustmentPercent
        p[ALMOST_DONE_SECONDS] = cfg.almostDoneSeconds
        if (cfg.nextTriggerMillis != null && cfg.nextTriggerMillis > 0L) {
            p[NEXT_TRIGGER_MILLIS] = cfg.nextTriggerMillis
        } else {
            p.remove(NEXT_TRIGGER_MILLIS)
        }
    }

    fun configFlow(ctx: Context): Flow<AlarmConfig> =
        ctx.dataStore.data.map { p -> fromPreferences(p) }

    suspend fun update(ctx: Context, transform: AlarmConfig.() -> AlarmConfig): AlarmConfig {
        var result = AlarmConfig()
        ctx.dataStore.edit { p ->
            val updated = fromPreferences(p).transform()
            writeToPreferences(p, updated)
            result = updated
        }
        return result
    }

    fun calculateAdjustedTimeAlone(currentSeconds: Long, adjustmentPercent: Int, isSuccess: Boolean): Long {
        val delta = maxOf(1L, Math.round(currentSeconds * (adjustmentPercent / 100.0)))
        return (if (isSuccess) currentSeconds + delta else currentSeconds - delta).coerceIn(1L, 14400L)
    }

    suspend fun updateTimeAlone(ctx: Context, isSuccess: Boolean) {
        ctx.dataStore.edit { p ->
            val current = p[TIME_ALONE] ?: 60L
            val pct = p[ADJUSTMENT_PERCENT] ?: 10
            p[TIME_ALONE] = calculateAdjustedTimeAlone(current, pct, isSuccess)
        }
    }

    suspend fun setActive(ctx: Context, active: Boolean, nextTriggerMillis: Long? = null) {
        ctx.dataStore.edit { p ->
            p[IS_ACTIVE] = active
            if (active && nextTriggerMillis != null && nextTriggerMillis > 0L) {
                p[NEXT_TRIGGER_MILLIS] = nextTriggerMillis
            } else {
                p.remove(NEXT_TRIGGER_MILLIS)
            }
        }
    }

    suspend fun setNextTriggerMillis(ctx: Context, millis: Long?) {
        ctx.dataStore.edit { p ->
            if (millis != null && millis > 0L) {
                p[NEXT_TRIGGER_MILLIS] = millis
            } else {
                p.remove(NEXT_TRIGGER_MILLIS)
            }
        }
    }

    suspend fun saveTimerSession(ctx: Context, session: PersistedTimerSession) {
        ctx.dataStore.edit { p ->
            p[SESSION_END_EPOCH] = session.endEpochMillis
            p[SESSION_INITIAL_SEC] = session.initialSeconds
            p[SESSION_HISTORY_ID] = session.historyId
            p[SESSION_ALMOST_DONE_SEC] = session.almostDoneSeconds
            p[SESSION_ALMOST_DONE_SHOWN] = session.almostDoneShown
            p[SESSION_DECISION_END_EPOCH] = session.decisionEndEpochMillis
        }
    }

    suspend fun updateTimerSessionAlmostDoneShown(ctx: Context, shown: Boolean) {
        ctx.dataStore.edit { p ->
            if (p.contains(SESSION_END_EPOCH)) {
                p[SESSION_ALMOST_DONE_SHOWN] = shown
            }
        }
    }

    suspend fun updateTimerSessionDecisionEnd(ctx: Context, decisionEndEpochMillis: Long) {
        ctx.dataStore.edit { p ->
            if (p.contains(SESSION_END_EPOCH)) {
                p[SESSION_DECISION_END_EPOCH] = decisionEndEpochMillis
            }
        }
    }

    suspend fun getTimerSession(ctx: Context): PersistedTimerSession? {
        val p = ctx.dataStore.data.first()
        val endEpoch = p[SESSION_END_EPOCH] ?: return null
        if (endEpoch <= 0L) return null
        return PersistedTimerSession(
            endEpochMillis = endEpoch,
            initialSeconds = p[SESSION_INITIAL_SEC] ?: 60L,
            historyId = p[SESSION_HISTORY_ID] ?: -1L,
            almostDoneSeconds = p[SESSION_ALMOST_DONE_SEC] ?: 60L,
            almostDoneShown = p[SESSION_ALMOST_DONE_SHOWN] ?: false,
            decisionEndEpochMillis = p[SESSION_DECISION_END_EPOCH] ?: 0L
        )
    }

    suspend fun clearTimerSession(ctx: Context) {
        ctx.dataStore.edit { p ->
            p.remove(SESSION_END_EPOCH)
            p.remove(SESSION_INITIAL_SEC)
            p.remove(SESSION_HISTORY_ID)
            p.remove(SESSION_ALMOST_DONE_SEC)
            p.remove(SESSION_ALMOST_DONE_SHOWN)
            p.remove(SESSION_DECISION_END_EPOCH)
        }
    }
}
