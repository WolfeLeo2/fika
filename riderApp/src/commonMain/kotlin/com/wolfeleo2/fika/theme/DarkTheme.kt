package com.wolfeleo2.fika.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.wolfeleo2.fika.settings.ThemeMode
import com.wolfeleo2.fika.settings.ThemeSettings
import org.koin.compose.koinInject

/** Whether the app is dark right now: the rider's choice, or the phone's setting when they chose System. */
@Composable
fun rememberDarkTheme(): Boolean {
    val mode by koinInject<ThemeSettings>().mode.collectAsState()
    return when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
}
