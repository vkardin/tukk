package io.github.vkardin.tukk.core.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.github.vkardin.tukk.core.model.ActionKind
import io.github.vkardin.tukk.core.model.ScreenMode
import io.github.vkardin.tukk.core.model.ThemeMode
import io.github.vkardin.tukk.core.model.TriggerConfig
import io.github.vkardin.tukk.core.model.TriggerKind
import io.github.vkardin.tukk.core.model.VolumeSequence
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferenceMappingTest {
    @Test fun defaultsWhenEmpty() {
        val prefs = mutablePreferencesOf()
        assertEquals(TriggerConfig(TriggerKind.SHAKE), prefs.trigger(TriggerKind.SHAKE))
        assertEquals(ThemeMode.DARK, prefs.settings().theme)
    }

    @Test fun legacyGlobalShakeSettingsBecomeTriggerDefaults() {
        val prefs = mutablePreferencesOf(
            floatPreferencesKey("threshold") to 29f,
            intPreferencesKey("cooldownMs") to 2000,
            intPreferencesKey("screenMode") to 2,
            booleanPreferencesKey("pocketGuard") to false,
        )
        val shake = prefs.trigger(TriggerKind.SHAKE)
        assertEquals(29f, shake.threshold)
        assertEquals(2000, shake.cooldownMs)
        assertEquals(ScreenMode.SCREEN_OFF, shake.screenMode)
        assertEquals(false, shake.pocketGuard)
    }

    @Test fun oldMinimumBrightnessIsNotCarriedOver() {
        val prefs = mutablePreferencesOf(intPreferencesKey("trigger.SHAKE.brightness") to 1)
        assertEquals(0, prefs.trigger(TriggerKind.SHAKE).torchLevel)
    }

    @Test fun roundTrip() {
        val config = TriggerConfig(
            kind = TriggerKind.VOLUME, added = true, enabled = true, action = ActionKind.SCREENSHOT,
            threshold = 20f, cooldownMs = 3000, screenMode = ScreenMode.SCREEN_ON, pocketGuard = false,
            vibration = false, hapticStrength = 2, pauseCharging = true, pauseCall = false, taps = 3,
            tapThreshold = 6f, volumeSequence = VolumeSequence.UP_DOWN, packageName = "a.b", torchLevel = 4,
        )
        val prefs = mutablePreferencesOf()
        prefs.write(config)
        assertEquals(config, prefs.trigger(TriggerKind.VOLUME))
    }

    @Test fun outOfRangeValuesAreClamped() {
        val prefs = mutablePreferencesOf(
            floatPreferencesKey("trigger.CHOP.threshold") to 100f,
            intPreferencesKey("trigger.CHOP.screen") to 9,
        )
        val chop = prefs.trigger(TriggerKind.CHOP)
        assertEquals(32f, chop.threshold)
        assertEquals(ScreenMode.ALWAYS, chop.screenMode)
    }
}
