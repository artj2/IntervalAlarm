package com.example.intervalalarm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun IntervalAlarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Dynamic color may not work on all emulators — fall back gracefully
    val colorScheme = try {
        if (darkTheme) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)
    } catch (_: Exception) {
        if (darkTheme) darkColorScheme() else lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}