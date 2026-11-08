package io.github.vkardin.tukk.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import io.github.vkardin.tukk.core.model.AppSettings
import io.github.vkardin.tukk.core.model.TriggerConfig
import io.github.vkardin.tukk.core.model.TriggerKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for gesture and app settings. */
@Singleton
class TukkRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val data: Flow<Preferences> = store.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val triggerFlow: Flow<List<TriggerConfig>> =
        data.map { prefs -> TriggerKind.entries.map(prefs::trigger) }.distinctUntilChanged()

    val settingsFlow: Flow<AppSettings> = data.map { it.settings() }.distinctUntilChanged()

    /** Latest triggers; holds defaults until the store has been read. */
    val triggers: StateFlow<List<TriggerConfig>> = triggerFlow.stateIn(
        scope, SharingStarted.Eagerly, TriggerKind.entries.map(::TriggerConfig),
    )

    val settings: StateFlow<AppSettings> = settingsFlow.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun loadSettings(): AppSettings = settingsFlow.first()

    suspend fun loadTriggers(): List<TriggerConfig> = triggerFlow.first()

    suspend fun save(config: TriggerConfig) {
        store.edit { it.write(config) }
    }

    /** Saves [config] in the application scope, for callers that are going away. */
    fun saveDetached(config: TriggerConfig) {
        scope.launch { save(config) }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs -> prefs.write(transform(prefs.settings())) }
    }

    suspend fun setServiceEnabled(enabled: Boolean) = updateSettings { it.copy(serviceEnabled = enabled) }
}
