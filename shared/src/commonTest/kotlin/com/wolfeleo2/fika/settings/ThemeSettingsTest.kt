package com.wolfeleo2.fika.settings

import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeThemeStore(override var mode: ThemeMode = ThemeMode.System) : ThemeStore

class ThemeSettingsTest {
    @Test fun startsFromTheStoredMode() {
        assertEquals(ThemeMode.Dark, ThemeSettings(FakeThemeStore(ThemeMode.Dark)).mode.value)
    }

    @Test fun setUpdatesTheFlowAndTheStore() {
        val store = FakeThemeStore()
        val settings = ThemeSettings(store)
        settings.set(ThemeMode.Light)
        assertEquals(ThemeMode.Light, settings.mode.value)
        assertEquals(ThemeMode.Light, store.mode)
    }
}
