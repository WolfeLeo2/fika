package com.wolfeleo2.fika

import android.content.Context
import com.wolfeleo2.fika.settings.ThemeMode
import com.wolfeleo2.fika.settings.ThemeStore

class PrefsThemeStore(context: Context) : ThemeStore {
    private val prefs = context.getSharedPreferences("fika", Context.MODE_PRIVATE)
    override var mode: ThemeMode
        get() = prefs.getString("theme_mode", null)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System
        set(value) = prefs.edit().putString("theme_mode", value.name).apply()
}
