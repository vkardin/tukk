package io.github.vkardin.tukk.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    @Test fun screenModeAllowsMatchingState() {
        assertTrue(ScreenMode.ALWAYS.allows(true))
        assertTrue(ScreenMode.ALWAYS.allows(false))
        assertTrue(ScreenMode.SCREEN_ON.allows(true))
        assertFalse(ScreenMode.SCREEN_ON.allows(false))
        assertTrue(ScreenMode.SCREEN_OFF.allows(false))
        assertFalse(ScreenMode.SCREEN_OFF.allows(true))
    }

    @Test fun defaultsMatchFirstLaunch() {
        val shake = TriggerConfig(TriggerKind.SHAKE)
        assertTrue(shake.added && shake.enabled)
        assertEquals(ActionKind.TORCH, shake.action)
        assertEquals(0, shake.torchLevel)
        assertEquals(ActionKind.VIBRATE, TriggerConfig(TriggerKind.FACE_DOWN).action)
        assertFalse(TriggerConfig(TriggerKind.CHOP).added)
    }

    @Test fun ordinalsStayStableForStoredSettings() {
        assertEquals(listOf("ALWAYS", "SCREEN_ON", "SCREEN_OFF"), ScreenMode.entries.map { it.name })
        assertEquals(listOf("DARK", "LIGHT", "SYSTEM"), ThemeMode.entries.map { it.name })
        assertEquals(listOf("DOUBLE_UP", "DOUBLE_DOWN", "UP_DOWN"), VolumeSequence.entries.map { it.name })
    }
}
