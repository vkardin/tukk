package io.github.vkardin.tukk.core.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.vkardin.tukk.core.model.ActionKind
import io.github.vkardin.tukk.core.model.AppSettings
import io.github.vkardin.tukk.core.model.ScreenMode
import io.github.vkardin.tukk.core.model.ThemeMode
import io.github.vkardin.tukk.core.model.TriggerConfig
import io.github.vkardin.tukk.core.model.TriggerKind
import io.github.vkardin.tukk.core.model.VolumeSequence

/**
 * Key names match the SharedPreferences file used up to version 0.3,
 * so the one-time migration keeps every existing setting.
 */
internal object Keys {
    val serviceEnabled = booleanPreferencesKey("enabled")
    val onboarded = booleanPreferencesKey("onboarded")
    val localeInitialized = booleanPreferencesKey("locale_initialized")
    val theme = intPreferencesKey("theme")
    val dynamicColor = booleanPreferencesKey("dynamicColor")

    // Global shake settings from 0.2, used as defaults for every trigger.
    val legacyThreshold = floatPreferencesKey("threshold")
    val legacyCooldown = intPreferencesKey("cooldownMs")
    val legacyScreen = intPreferencesKey("screenMode")
    val legacyPocket = booleanPreferencesKey("pocketGuard")
    val legacyVibration = booleanPreferencesKey("vibration")
    val legacyCharging = booleanPreferencesKey("pauseCharging")

    class Trigger(kind: TriggerKind) {
        private val prefix = "trigger.${kind.name}."
        val added = booleanPreferencesKey(prefix + "added")
        val enabled = booleanPreferencesKey(prefix + "enabled")
        val action = stringPreferencesKey(prefix + "action")
        val threshold = floatPreferencesKey(prefix + "threshold")
        val cooldown = intPreferencesKey(prefix + "cooldown")
        val screen = intPreferencesKey(prefix + "screen")
        val pocket = booleanPreferencesKey(prefix + "pocket")
        val haptic = booleanPreferencesKey(prefix + "haptic")
        val hapticStrength = intPreferencesKey(prefix + "hapticStrength")
        val charging = booleanPreferencesKey(prefix + "charging")
        val call = booleanPreferencesKey(prefix + "call")
        val taps = intPreferencesKey(prefix + "taps")
        val tapThreshold = floatPreferencesKey(prefix + "tapThreshold")
        val volumeMode = intPreferencesKey(prefix + "volumeMode")
        val packageName = stringPreferencesKey(prefix + "package")

        // The former "brightness" key defaulted to the lowest level and is intentionally not read.
        val torchLevel = intPreferencesKey(prefix + "torchLevel")
    }

    val triggers = TriggerKind.entries.associateWith(::Trigger)
}

private inline fun <reified T : Enum<T>> ordinal(value: Int?, fallback: T): T =
    enumValues<T>().getOrNull(value ?: -1) ?: fallback

internal fun Preferences.settings() = AppSettings(
    theme = ordinal(this[Keys.theme], ThemeMode.DARK),
    dynamicColor = this[Keys.dynamicColor] ?: false,
    serviceEnabled = this[Keys.serviceEnabled] ?: false,
    onboarded = this[Keys.onboarded] ?: false,
    localeInitialized = this[Keys.localeInitialized] ?: false,
)

internal fun MutablePreferences.write(settings: AppSettings) {
    this[Keys.theme] = settings.theme.ordinal
    this[Keys.dynamicColor] = settings.dynamicColor
    this[Keys.serviceEnabled] = settings.serviceEnabled
    this[Keys.onboarded] = settings.onboarded
    this[Keys.localeInitialized] = settings.localeInitialized
}

internal fun Preferences.trigger(kind: TriggerKind): TriggerConfig {
    val keys = Keys.triggers.getValue(kind)
    val defaults = TriggerConfig(kind)
    return defaults.copy(
        added = this[keys.added] ?: defaults.added,
        enabled = this[keys.enabled] ?: defaults.enabled,
        action = ActionKind.entries.firstOrNull { it.name == this[keys.action] } ?: defaults.action,
        threshold = (this[keys.threshold] ?: this[Keys.legacyThreshold] ?: defaults.threshold)
            .coerceIn(TriggerConfig.THRESHOLD_RANGE),
        cooldownMs = (this[keys.cooldown] ?: this[Keys.legacyCooldown] ?: defaults.cooldownMs)
            .coerceIn(TriggerConfig.COOLDOWN_RANGE),
        screenMode = ordinal(this[keys.screen] ?: this[Keys.legacyScreen], ScreenMode.ALWAYS),
        pocketGuard = this[keys.pocket] ?: this[Keys.legacyPocket] ?: defaults.pocketGuard,
        vibration = this[keys.haptic] ?: this[Keys.legacyVibration] ?: defaults.vibration,
        hapticStrength = (this[keys.hapticStrength] ?: defaults.hapticStrength).coerceIn(0, 2),
        pauseCharging = this[keys.charging] ?: this[Keys.legacyCharging] ?: defaults.pauseCharging,
        pauseCall = this[keys.call] ?: defaults.pauseCall,
        taps = (this[keys.taps] ?: defaults.taps).coerceIn(2, 3),
        tapThreshold = (this[keys.tapThreshold] ?: defaults.tapThreshold).coerceIn(TriggerConfig.TAP_THRESHOLD_RANGE),
        volumeSequence = ordinal(this[keys.volumeMode], defaults.volumeSequence),
        packageName = this[keys.packageName] ?: "",
        torchLevel = (this[keys.torchLevel] ?: 0).coerceAtLeast(0),
    )
}

internal fun MutablePreferences.write(config: TriggerConfig) {
    val keys = Keys.triggers.getValue(config.kind)
    this[keys.added] = config.added
    this[keys.enabled] = config.enabled
    this[keys.action] = config.action.name
    this[keys.threshold] = config.threshold
    this[keys.cooldown] = config.cooldownMs
    this[keys.screen] = config.screenMode.ordinal
    this[keys.pocket] = config.pocketGuard
    this[keys.haptic] = config.vibration
    this[keys.hapticStrength] = config.hapticStrength
    this[keys.charging] = config.pauseCharging
    this[keys.call] = config.pauseCall
    this[keys.taps] = config.taps
    this[keys.tapThreshold] = config.tapThreshold
    this[keys.volumeMode] = config.volumeSequence.ordinal
    this[keys.packageName] = config.packageName
    this[keys.torchLevel] = config.torchLevel
}
