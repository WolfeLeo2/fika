package com.wolfeleo2.fika.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which look the app uses. System follows the phone's own dark mode setting. */
enum class ThemeMode { System, Light, Dark }

/** Remembers the chosen mode on the device between launches. */
interface ThemeStore {
    var mode: ThemeMode
}

/** The rider's theme choice, observable so the whole UI re-themes the moment it changes. */
class ThemeSettings(private val store: ThemeStore) {
    private val _mode = MutableStateFlow(store.mode)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    fun set(mode: ThemeMode) {
        store.mode = mode
        _mode.value = mode
    }
}
