package com.example.intervalalarm.model

data class AlarmConfig(
    val startHour: Int = 8,
    val startMinute: Int = 0,
    val endHour: Int = 16,
    val endMinute: Int = 30,
    val minIntervalMin: Int = 30,
    val maxIntervalMin: Int = 60,
    val notificationEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val soundUri: String? = null,
    val isActive: Boolean = false,
    val repeatDays: Set<Int> = emptySet(),
    val timeAloneSeconds: Long = 60L,
    val adjustmentPercent: Int = 10
) {
    val windowMinutes: Int
        get() {
            val s = startHour * 60 + startMinute
            val e = endHour * 60 + endMinute
            return if (e > s) e - s else 0
        }
}
