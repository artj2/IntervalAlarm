package com.example.intervalalarm.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.intervalalarm.model.AlarmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "alarm_prefs")

object AlarmPreferences {

    private val START_HOUR = intPreferencesKey("start_hour")
    private val START_MIN = intPreferencesKey("start_min")
    private val END_HOUR = intPreferencesKey("end_hour")
    private val END_MIN = intPreferencesKey("end_min")
    private val MIN_INTERVAL = intPreferencesKey("min_interval")
    private val MAX_INTERVAL = intPreferencesKey("max_interval")
    private val NOTIF_ON = booleanPreferencesKey("notif_on")
    private val SOUND_ON = booleanPreferencesKey("sound_on")
    private val VIB_ON = booleanPreferencesKey("vib_on")
    private val SOUND_URI = stringPreferencesKey("sound_uri")
    private val IS_ACTIVE = booleanPreferencesKey("is_active")
    private val REPEAT_DAYS = stringPreferencesKey("repeat_days")
    private val TIME_ALONE = longPreferencesKey("time_alone")

    fun configFlow(ctx: Context): Flow<AlarmConfig> =
        ctx.dataStore.data.map { p ->
            AlarmConfig(
                startHour = p[START_HOUR] ?: 8,
                startMinute = p[START_MIN] ?: 0,
                endHour = p[END_HOUR] ?: 16,
                endMinute = p[END_MIN] ?: 30,
                minIntervalMin = p[MIN_INTERVAL] ?: 40,
                maxIntervalMin = p[MAX_INTERVAL] ?: 80,
                notificationEnabled = p[NOTIF_ON] ?: true,
                soundEnabled = p[SOUND_ON] ?: true,
                vibrationEnabled = p[VIB_ON] ?: true,
                soundUri = p[SOUND_URI],
                isActive = p[IS_ACTIVE] ?: false,
                repeatDays = p[REPEAT_DAYS]
                    ?.split(",")
                    ?.mapNotNull { it.trim().toIntOrNull() }
                    ?.toSet()
                    ?: emptySet(),
                timeAloneSeconds = p[TIME_ALONE] ?: 600L
            )
        }

    suspend fun save(ctx: Context, cfg: AlarmConfig) {
        ctx.dataStore.edit { p ->
            p[START_HOUR] = cfg.startHour
            p[START_MIN] = cfg.startMinute
            p[END_HOUR] = cfg.endHour
            p[END_MIN] = cfg.endMinute
            p[MIN_INTERVAL] = cfg.minIntervalMin
            p[MAX_INTERVAL] = cfg.maxIntervalMin
            p[NOTIF_ON] = cfg.notificationEnabled
            p[SOUND_ON] = cfg.soundEnabled
            p[VIB_ON] = cfg.vibrationEnabled
            if (cfg.soundUri != null) p[SOUND_URI] = cfg.soundUri
            else p.remove(SOUND_URI)
            p[IS_ACTIVE] = cfg.isActive
            p[REPEAT_DAYS] = cfg.repeatDays.joinToString(",")
            p[TIME_ALONE] = cfg.timeAloneSeconds
        }
    }

    suspend fun updateTimeAlone(ctx: Context, multiplier: Double) {
        ctx.dataStore.edit { p ->
            val current = p[TIME_ALONE] ?: 600L
            val newVal = (current * multiplier).toLong().coerceIn(1L, 14400L)
            p[TIME_ALONE] = newVal
        }
    }

    suspend fun setActive(ctx: Context, active: Boolean) {
        ctx.dataStore.edit { it[IS_ACTIVE] = active }
    }
}
